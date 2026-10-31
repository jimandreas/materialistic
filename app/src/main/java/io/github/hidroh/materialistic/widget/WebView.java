/*
 * Copyright (c) 2016 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.hidroh.materialistic.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;

import io.github.hidroh.materialistic.AppUtils;
import io.github.hidroh.materialistic.annotation.Synthetic;

public class WebView extends android.webkit.WebView {
    static final String BLANK = "about:blank";
    static final String FILE = "file:///";
    private final HistoryWebViewClient mClient = new HistoryWebViewClient();
    @Synthetic String mPendingUrl, mPendingHtml;
    private final int[] mScrollConsumed = new int[2];
    private final int mTouchSlop;
    private float mDownRawY, mLastRawY, mWebViewY, mOffsetY;
    private boolean mDragging;

    public WebView(Context context) {
        this(context, null);
    }

    public WebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setWebViewClient(mClient);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setNestedScrollingEnabled(true);
    }

    /**
     * Offers vertical drags to nested scrolling parents (e.g. a collapsing AppBarLayout) before
     * scrolling web content, so this view can have a fixed height instead of being wrapped in a
     * scroll view. A wrap_content WebView gets a zero-height CSS viewport, which breaks pages
     * that size content with vh units.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isNestedScrollingEnabled()) {
            return super.onTouchEvent(ev);
        }
        MotionEvent event = MotionEvent.obtain(ev);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastRawY = event.getRawY();
                mDownRawY = mLastRawY;
                mWebViewY = event.getY();
                mOffsetY = 0;
                mDragging = false;
                startNestedScroll(SCROLL_AXIS_VERTICAL);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && Math.abs(event.getRawY() - mDownRawY) > mTouchSlop) {
                    mDragging = true;
                }
                if (mDragging) {
                    // positive when content should move up, i.e. finger moving up
                    int dy = Math.round(mLastRawY - event.getRawY());
                    mLastRawY -= dy;
                    if (dispatchNestedPreScroll(0, dy, mScrollConsumed, null)) {
                        dy -= mScrollConsumed[1];
                    }
                    // feed web content only the part of the drag not consumed by parents
                    mWebViewY -= dy;
                    mOffsetY = mWebViewY - event.getY();
                    event.offsetLocation(0, mOffsetY);
                    boolean handled = super.onTouchEvent(event);
                    // web content cannot scroll past its edges, let parents (e.g. app bar
                    // expanding at top) handle the rest
                    int unconsumed = dy != 0 && !canScrollVertically(dy) ? dy : 0;
                    dispatchNestedScroll(0, dy - unconsumed, 0, unconsumed, null);
                    event.recycle();
                    return handled;
                }
                mWebViewY = event.getY() + mOffsetY;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopNestedScroll();
                break;
        }
        event.offsetLocation(0, mOffsetY);
        boolean handled = super.onTouchEvent(event);
        event.recycle();
        return handled;
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mClient.wrap(client);
    }

    @Override
    public boolean canGoBack() {
        return TextUtils.isEmpty(mPendingUrl) && super.canGoBack();
    }

    public void reloadUrl(String url) {
        if (getProgress() < 100) {
            stopLoading(); // this will fire onPageFinished for current URL
        }
        mPendingUrl = url;
        loadUrl(BLANK); // clear current web resources, load pending URL upon onPageFinished
    }

    public void reloadHtml(String html) {
        mPendingHtml = html;
        reloadUrl(FILE);
    }

    static class HistoryWebViewClient extends WebViewClient {
        private WebViewClient mClient;

        @Override
        public void onPageStarted(android.webkit.WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.pageUp(true);
            WebView webView = (WebView) view;
            if (!TextUtils.equals(url, BLANK) && !TextUtils.isEmpty(webView.mPendingUrl)) {
                view.setVisibility(VISIBLE);
            }
            if (mClient != null) {
                mClient.onPageStarted(view, url, favicon);
            }
        }

        @Override
        public void onPageFinished(android.webkit.WebView view, String url) {
            super.onPageFinished(view, url);
            WebView webView = (WebView) view;
            if (TextUtils.equals(url, BLANK)) { // has pending reload, open corresponding URL
                if (!TextUtils.isEmpty(webView.mPendingHtml)) {
                    view.loadDataWithBaseURL(webView.mPendingUrl, webView.mPendingHtml,
                            "text/html", "UTF-8", webView.mPendingUrl);
                } else {
                    view.loadUrl(webView.mPendingUrl);
                }
            } else if (!TextUtils.isEmpty(webView.mPendingUrl)) { // reload done, clear history
                webView.mPendingUrl = null;
                webView.mPendingHtml = null;
                view.clearHistory();
            }
            if (mClient != null) {
                mClient.onPageFinished(view, url);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, String url) {
            return mClient != null ? mClient.shouldInterceptRequest(view, url) :
                    super.shouldInterceptRequest(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, WebResourceRequest request) {
            return mClient != null ? mClient.shouldInterceptRequest(view, request) :
                    super.shouldInterceptRequest(view, request);
        }

        @Synthetic
        void wrap(WebViewClient client) {
            mClient = client;
        }
    }
}

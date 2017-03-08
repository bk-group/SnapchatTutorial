package com.rja.snapchat.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewAnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.rja.snapchat.R;
import com.rja.snapchat.VideoCache;
import com.rja.snapchat.models.IStory;
import com.rja.snapchat.models.IStoryItem;
import com.rja.snapchat.util.Print;

/**
 * Created by rjaylward on 3/5/17
 */

public class StoryViewer extends RelativeLayout {

    private static final String TAG = StoryViewer.class.getSimpleName();

    private ImageView mImageView;
    private ProgressBar mProgressBar;

    private IStory mStory;
    private IStoryItem mCurrentItem;
    private SurfaceVideoHelper mVideoHelper;
    private boolean mIsShowing;

    private Handler mHandler;
    private StoryViewListener mListener;

    public StoryViewer(Context context) {
        super(context);
        init();
    }

    public StoryViewer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StoryViewer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        LayoutInflater.from(getContext()).inflate(R.layout.view_story_viewer, this, true);
        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.loading_background));

        mImageView = (ImageView) findViewById(R.id.vsv_image);
        mProgressBar = (ProgressBar) findViewById(R.id.vsv_loading);
        mHandler = new Handler();

        mVideoHelper = new SurfaceVideoHelper(this);
        mVideoHelper.setCallback(new SurfaceVideoHelper.Callback() {

            @Override
            public void onMediaPlayerPrepared(String url) {
                mImageView.setVisibility(GONE);
            }

            @Override
            public void onPlaybackStatusChanged(int playbackState) {
                switch(playbackState) {
                    case PlaybackStateCompat.STATE_BUFFERING:
                        mProgressBar.setVisibility(VISIBLE);
                        break;
                    case PlaybackStateCompat.STATE_PLAYING:
                        mProgressBar.setVisibility(GONE);
                        break;
                }
            }

            @Override
            public void onError(Exception e) {
                Print.e(TAG, e, "video onError");
                next();
            }

            @Override
            public void onCompletion() {
                Print.d(TAG, "video onCompletion");
                next();
            }

        });
    }

    public void loadStory(IStory story) {
        mStory = story;
    }

    private void showCurrentItem() {
        Print.d(TAG, "showCurrentItem");
        if(mStory != null) {
            mCurrentItem = mStory.getCurrentStoryItem();
            if(mCurrentItem != null) {
                Print.d(mCurrentItem.getVideoUrl(), mCurrentItem.getImageUrl(), mCurrentItem.getDuration());
                if(mCurrentItem.getVideoUrl() != null) {
                    mVideoHelper.prepare(VideoCache.getProxy().getProxyUrl(mCurrentItem.getVideoUrl()), true);
                }
                else if(mCurrentItem.getImageUrl() != null) {
                    showImage(mCurrentItem);
                }
            }
            else {
                finished();
            }
        }
    }

    private void finished() {
        Print.d(TAG, "finished()");

        for(IStoryItem iStoryItem : mStory.getStoryItems()) {
            Print.v(TAG, iStoryItem.isViewed(), iStoryItem.getImageUrl(), iStoryItem.getVideoUrl());
        }

        mImageView.setVisibility(VISIBLE);
        mVideoHelper.destroy();
        hide();
    }

    private void showImage(final IStoryItem item) {
        mProgressBar.setVisibility(VISIBLE);
        mImageView.setVisibility(VISIBLE);
        Glide.with(getContext()).load(item.getImageUrl())
                .centerCrop()
                .crossFade()
                .listener(new RequestListener<String, GlideDrawable>() {

                    @Override
                    public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                        mProgressBar.setVisibility(GONE);
                        Print.e(TAG, e, "image onException");
                        next();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                        mProgressBar.setVisibility(GONE);
                        mHandler.postDelayed(mShowImageRunnable, item.getDuration());
                        Print.d(TAG, "image onResourceReady");
                        return false;
                    }
                })
                .into(mImageView);
    }

    private Runnable mShowImageRunnable = new Runnable() {

        @Override
        public void run() {
            mImageView.setImageResource(R.drawable.black_rectangle);
            Print.d(TAG,"showingImageRunnable complete");
            next();
        }

    };

    private void next() {
        Print.d(TAG, "next", mCurrentItem.getVideoUrl(), mCurrentItem.getImageUrl());
        if(mCurrentItem != null)
            mCurrentItem.setViewed(true);

        showCurrentItem();
    }

    private int mStartRadius;
    private int mEndRadius;
    private int mRevealOriginX;
    private int mRevealOriginY;

    public void show(int originX, int originY) {
        mRevealOriginX = originX;
        mRevealOriginY = originY;

        setVisibility(VISIBLE);
        mStartRadius = 0;
        mEndRadius = (int) Math.hypot(getWidth(), getHeight());

        Animator anim = ViewAnimationUtils.createCircularReveal(this, mRevealOriginX, mRevealOriginY, mStartRadius, mEndRadius);
        anim.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                showCurrentItem();
            }

        });
        anim.start();
    }

    public void hide() {
        Animator anim = ViewAnimationUtils.createCircularReveal(this, mRevealOriginX, mRevealOriginY, mEndRadius, mStartRadius);
        anim.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                setVisibility(GONE);
                if(mListener != null)
                    mListener.onStoryHidden();
            }

        });
        anim.start();
    }

    public void setListener(StoryViewListener listener) {
        mListener = listener;
    }

    public interface StoryViewListener {
        void onStoryHidden();
    }
}
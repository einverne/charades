package com.pongo.charades.activities;

import android.content.Intent;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pongo.charades.R;
import com.pongo.charades.adapters.CharadesRecyclerViewAdapter;
import com.pongo.charades.adapters.ScoreTrackRecylerViewAdapter;
import com.pongo.charades.models.CategoryItemModel;
import com.pongo.charades.models.CategoryModel;
import com.pongo.charades.modules.FontAwesomeProvider;
import com.pongo.charades.services.SoundService;
import com.pongo.charades.services.TiltSensorService;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Inject;

import io.realm.Realm;

public class GameRoundActivity extends BaseActivity implements TiltSensorService.TiltEventListener {
    public static final String CATEGORY_TITLE = "CATEGORY_TITLE";

    private enum State {
        COUNTDOWN,
        PLAYING,
        GAME_OVER
    }

    @Inject
    FontAwesomeProvider mFontAwesome;
    private CategoryModel mCategory;
    private ArrayList<CategoryItemModel> mItems;
    private int mCurrentItemIndex;
    private CategoryItemModel mCurrentItem;
    private State mState;

    private TextView mMainText;
    private TextView mTopText;
    private LinearLayout mSkipButton;
    private LinearLayout mBackButton;
    private LinearLayout mReplayButton;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;

    private Bundle mExtras;
    private TiltSensorService mTiltSensor;
    private SoundService mSoundService;
    private SoundPool mSoundPool;

    private int mScore;
    private ScoreTrackRecylerViewAdapter mScoreTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_round);
        mState = State.COUNTDOWN;
        Intent intent = getIntent();
        mExtras = intent.getExtras();
        mTiltSensor = new TiltSensorService(this, this);
        mSoundService = new SoundService(this);
        mScoreTrack = new ScoreTrackRecylerViewAdapter(this);

        mMainText = (TextView) findViewById(R.id.main_text);
        mTopText = (TextView) findViewById(R.id.top_text);
        mSkipButton = (LinearLayout) findViewById(R.id.skip_button);
        mBackButton = (LinearLayout) findViewById(R.id.back_button);
        mReplayButton = (LinearLayout) findViewById(R.id.replay_button);

        mRecyclerView = (RecyclerView) findViewById(R.id.score_track_recycler_view);

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mScoreTrack);

        ((TextView)findViewById(R.id.skip_icon)).setTypeface(mFontAwesome.getTypeface());
        ((TextView)findViewById(R.id.back_icon)).setTypeface(mFontAwesome.getTypeface());
        ((TextView)findViewById(R.id.replay_icon)).setTypeface(mFontAwesome.getTypeface());

        mMainText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeWord(true);
            }
        });

        mSkipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeWord(false);
            }
        });

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mReplayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });

        loadCategory();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTiltSensor.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTiltSensor.pause();
    }

    @Override
    public void onTiltChanged(TiltSensorService.State oldState, TiltSensorService.State newState) {
        if (mState != State.PLAYING) return;

        switch (newState) {
            case UPWARDS:
                changeWord(true);
                break;
            case DOWNWARDS:
                changeWord(false);
                break;
        }
    }

    private void loadCategory() {
        String categoryTitle = mExtras.getString(CATEGORY_TITLE);
        Realm realm = Realm.getInstance(getApplicationContext());
        try {
            mCategory = realm.where(CategoryModel.class)
                                          .equalTo("title", categoryTitle)
                                          .findFirst();

            mItems = new ArrayList<>(mCategory.getItems());

            // Shuffle up and deal!
            Random rng;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                rng = ThreadLocalRandom.current();
            } else {
                rng = new Random();
            }
            for (int i = mItems.size() - 1; i > 0; i--) {
                int index = rng.nextInt(i + 1);
                CategoryItemModel item = mItems.get(index);
                mItems.set(index, mItems.get(i));
                mItems.set(i, item);
            }
        } finally {
            realm.close();
        }
    }

    private void hide() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mMainText.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void setupRoundTimer() {
        new CountDownTimer(10000 + 500, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secondsLeft = millisUntilFinished / 1000;
                mTopText.setText(String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60));
            }

            @Override
            public void onFinish() {
                mScoreTrack.add(mCurrentItem.getValue(), false);
                mState = State.GAME_OVER;

                mSkipButton.setVisibility(View.INVISIBLE);
                mBackButton.setVisibility(View.VISIBLE);
                mReplayButton.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mMainText.setVisibility(View.GONE);
                mScoreTrack.notifyDataSetChanged();
                mTopText.setText("Score: " + mScore);
                mSoundService.playFinish();
            }
        }.start();
    }

    private void changeWord(Boolean score) {
        if (mState != State.PLAYING) return;

        if (score) {
            mSoundService.playSuccess();
            mScore++;
        } else {
            mSoundService.playSkip();
        }

        if (mCurrentItem != null) {
            mScoreTrack.add(mCurrentItem.getValue(), score);
        }

        mCurrentItemIndex = (mCurrentItemIndex + 1) % mItems.size();
        mCurrentItem = mItems.get(mCurrentItemIndex);
        mMainText.setText(mCurrentItem.getValue());
    }

    private void start() {
        mTopText.setText(mCategory.getTitle());
        mSkipButton.setVisibility(View.INVISIBLE);
        mBackButton.setVisibility(View.INVISIBLE);
        mReplayButton.setVisibility(View.INVISIBLE);
        mRecyclerView.setVisibility(View.GONE);
        mMainText.setVisibility(View.VISIBLE);
        hide();
        new CountDownTimer(3500, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mMainText.setText(String.valueOf(millisUntilFinished / 1000));
                mSoundService.playTick();
            }

            @Override
            public void onFinish() {
                mState = State.PLAYING;
                mScore = 0;
                mScoreTrack.clear();
                mSkipButton.setVisibility(View.VISIBLE);
                setupRoundTimer();
                changeWord(false);
                mSoundService.playStart();
            }
        }.start();
    }
}

package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import android.content.Context;
import android.os.Build;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackEngineController.OnBufferSelected;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.OnFormatSelected;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.FormatItem.Preset;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HqDialogManager extends PlayerEventListenerHelper {
    private static final String TAG = HqDialogManager.class.getSimpleName();
    private static final int VIDEO_FORMATS_ID = 132;
    private static final int AUDIO_FORMATS_ID = 133;
    private static final int VIDEO_BUFFER_ID = 134;
    private static final int BACKGROUND_PLAYBACK_ID = 135;
    private static final int VIDEO_PRESETS_ID = 136;
    private AppSettingsPresenter mSettingsPresenter;
    // NOTE: using map, because same item could be changed time to time
    private final Map<Integer, OptionCategory> mCheckedCategories = new LinkedHashMap<>();
    private final Map<Integer, OptionCategory> mRadioCategories = new LinkedHashMap<>();
    private final Map<Integer, OptionCategory> mSingleOptions = new LinkedHashMap<>();
    private final Set<Runnable> mHideListeners = new HashSet<>();
    private final StateUpdater mStateUpdater;
    private PlayerData mPlayerData;

    public HqDialogManager(StateUpdater stateUpdater) {
        mStateUpdater = stateUpdater;
    }

    @Override
    public void onInitDone() {
        mPlayerData = PlayerData.instance(getActivity());
        mSettingsPresenter = AppSettingsPresenter.instance(getActivity());
    }

    @Override
    public void onViewResumed() {
        getController().setBufferType(mPlayerData.getVideoBufferType());
    }

    @Override
    public void onEngineInitialized() {
        updateBackgroundPlayback();
    }

    @Override
    public void onHighQualityClicked() {
        mSettingsPresenter.clear();

        addQualityCategories();
        addVideoBufferCategory();
        addPresetsCategory();
        addBackgroundPlaybackCategory();

        internalStuff();

        mSettingsPresenter.showDialog(getActivity().getString(R.string.playback_settings), this::onDialogHide);
    }

    private void addQualityCategories() {
        List<FormatItem> videoFormats = getController().getVideoFormats();
        String videoFormatsTitle = getActivity().getString(R.string.title_video_formats);

        List<FormatItem> audioFormats = getController().getAudioFormats();
        String audioFormatsTitle = getActivity().getString(R.string.title_audio_formats);

        addRadioCategory(OptionCategory.from(
                VIDEO_FORMATS_ID,
                videoFormatsTitle,
                UiOptionItem.from(videoFormats, this::selectFormatOption)));
        addRadioCategory(OptionCategory.from(
                AUDIO_FORMATS_ID,
                audioFormatsTitle,
                UiOptionItem.from(audioFormats, this::selectFormatOption)));
    }

    private void selectFormatOption(OptionItem option) {
        getController().selectFormat(UiOptionItem.toFormat(option));
        if (getController().hasNoMedia()) {
            getController().reloadPlayback();
        }
    }

    private void addVideoBufferCategory() {
        addRadioCategory(createVideoBufferCategory(getActivity(), mPlayerData, type -> {
            getController().setBufferType(type);
        }));
    }

    private void internalStuff() {
        appendRadioOptions();
        appendCheckedOptions();
        appendSingleOptions();
    }

    private void onDialogHide() {
        updateBackgroundPlayback();

        for (Runnable listener : mHideListeners) {
            listener.run();
        }
    }

    private void updateBackgroundPlayback() {
        ViewManager.instance(getActivity()).blockTop(null);

        getController().setPlaybackMode(mPlayerData.getPlaybackMode());
    }

    private void addBackgroundPlaybackCategory() {
        OptionCategory category =
                createBackgroundPlaybackCategory(getActivity(), mPlayerData, this::updateBackgroundPlayback);

        addRadioCategory(category);
    }

    private void addPresetsCategory() {
        addRadioCategory(createVideoPresetsCategory(getActivity(), mPlayerData, format -> getController().selectFormat(format)));
    }

    public void addSingleOption(OptionCategory category) {
        mSingleOptions.put(category.id, category);
    }

    public void addCheckedCategory(OptionCategory category) {
        mCheckedCategories.put(category.id, category);
    }

    public void removeCategory(int id) {
        mCheckedCategories.remove(id);
    }

    public void addRadioCategory(OptionCategory category) {
        mRadioCategories.put(category.id, category);
    }

    public void addOnDialogHide(Runnable listener) {
        mHideListeners.add(listener);
    }

    public void removeOnDialogHide(Runnable listener) {
        mHideListeners.remove(listener);
    }

    private void appendSingleOptions() {
        for (OptionCategory category : mSingleOptions.values()) {
            mSettingsPresenter.appendSingleSwitch(category.option);
        }
    }

    private void appendCheckedOptions() {
        for (OptionCategory category : mCheckedCategories.values()) {
            mSettingsPresenter.appendCheckedCategory(category.title, category.options);
        }
    }

    private void appendRadioOptions() {
        for (OptionCategory category : mRadioCategories.values()) {
            mSettingsPresenter.appendRadioCategory(category.title, category.options);
        }
    }

    public static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData) {
        return createBackgroundPlaybackCategory(context, playerData, () -> {});
    }

    private static OptionCategory createBackgroundPlaybackCategory(Context context, PlayerData playerData, Runnable onSetCallback) {
        String categoryTitle = context.getString(R.string.category_background_playback);

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_off),
                optionItem -> {
                    playerData.setPlaybackMode(PlaybackEngineController.PLAYBACK_MODE_DEFAULT);
                    onSetCallback.run();
                }, playerData.getPlaybackMode() == PlaybackEngineController.PLAYBACK_MODE_DEFAULT));
        if (Helpers.isAndroidTV(context) && Build.VERSION.SDK_INT < 26) { // useful only for pre-Oreo UI
            options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_behind) + " (Android TV 5,6,7)",
                    optionItem -> {
                        playerData.setPlaybackMode(PlaybackEngineController.PLAYBACK_MODE_PLAY_BEHIND);
                        onSetCallback.run();
                    }, playerData.getPlaybackMode() == PlaybackEngineController.PLAYBACK_MODE_PLAY_BEHIND));
        }
        options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_pip),
                optionItem -> {
                    playerData.setPlaybackMode(PlaybackEngineController.PLAYBACK_MODE_PIP);
                    onSetCallback.run();
                }, playerData.getPlaybackMode() == PlaybackEngineController.PLAYBACK_MODE_PIP));
        options.add(UiOptionItem.from(context.getString(R.string.option_background_playback_only_audio),
                optionItem -> {
                    playerData.setPlaybackMode(PlaybackEngineController.PLAYBACK_MODE_BACKGROUND_PLAY);
                    onSetCallback.run();
                }, playerData.getPlaybackMode() == PlaybackEngineController.PLAYBACK_MODE_BACKGROUND_PLAY));

        return OptionCategory.from(BACKGROUND_PLAYBACK_ID, categoryTitle, options);
    }

    public static OptionCategory createVideoPresetsCategory(Context context, PlayerData playerData) {
        return createVideoPresetsCategory(context, playerData, format -> {});
    }

    private static OptionCategory createVideoPresetsCategory(Context context, PlayerData playerData, OnFormatSelected onFormatSelected) {
        Preset[] presets = {
                new Preset("SD     30fps    avc", "640,360,30,avc"),
                new Preset("SD     30fps    vp9", "640,360,30,vp9"),
                new Preset("SD     60fps    avc", "640,360,60,avc"),
                new Preset("SD     60fps    vp9", "640,360,60,vp9"),
                new Preset("HD     30fps    avc", "1280,720,30,avc"),
                new Preset("HD     30fps    vp9", "1280,720,30,vp9"),
                new Preset("HD     60fps    avc", "1280,720,60,avc"),
                new Preset("HD     60fps    vp9", "1280,720,60,vp9"),
                new Preset("FHD    30fps    avc", "1920,1080,30,avc"),
                new Preset("FHD    30fps    vp9", "1920,1080,30,vp9"),
                new Preset("FHD    30fps    vp9+hdr", "1920,1080,30,vp9.2"),
                new Preset("FHD    60fps    avc", "1920,1080,60,avc"),
                new Preset("FHD    60fps    vp9", "1920,1080,60,vp9"),
                new Preset("FHD    60fps    vp9+hdr", "1920,1080,60,vp9.2"),
                new Preset("2K     30fps    vp9", "2560,1440,30,vp9"),
                new Preset("2K     30fps    vp9+hdr", "2560,1440,30,vp9.2"),
                new Preset("2K     60fps    vp9", "2560,1440,60,vp9"),
                new Preset("2K     60fps    vp9+hdr", "2560,1440,60,vp9.2"),
                new Preset("4K     30fps    vp9", "3840,2160,30,vp9"),
                new Preset("4K     30fps    vp9+hdr", "3840,2160,30,vp9.2"),
                new Preset("4K     60fps    vp9", "3840,2160,60,vp9"),
                new Preset("4K     60fps    vp9+hdr", "3840,2160,60,vp9.2"),
                new Preset("8K     30fps    vp9", "7680,4320,30,vp9"),
                new Preset("8K     30fps    vp9+hdr", "7680,4320,30,vp9.2"),
                new Preset("8K     60fps    vp9", "7680,4320,60,vp9"),
                new Preset("8K     60fps    vp9+hdr", "7680,4320,60,vp9.2")
        };

        return OptionCategory.from(
                VIDEO_PRESETS_ID,
                context.getString(R.string.title_video_presets),
                fromPresets(presets, playerData, onFormatSelected));
    }

    private static List<OptionItem> fromPresets(Preset[] presets, PlayerData playerData, OnFormatSelected onFormatSelected) {
        List<OptionItem> result = new ArrayList<>();

        for (Preset preset : presets) {
            result.add(0, UiOptionItem.from(preset.name,
                    option -> {
                        playerData.setFormat(preset.format);
                        onFormatSelected.onFormatSelected(preset.format);
                    },
                    preset.format.equals(playerData.getFormat(FormatItem.TYPE_VIDEO))));
        }

        return result;
    }

    public static OptionCategory createVideoBufferCategory(Context context, PlayerData playerData) {
        return createVideoBufferCategory(context, playerData, type -> {});
    }

    private static OptionCategory createVideoBufferCategory(Context context, PlayerData playerData, OnBufferSelected onBufferSelected) {
        String videoBufferTitle = context.getString(R.string.video_buffer);
        List<OptionItem> optionItems = new ArrayList<>();
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_low, PlaybackEngineController.BUFFER_LOW, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_med, PlaybackEngineController.BUFFER_MED, onBufferSelected));
        optionItems.add(createVideoBufferOption(context, playerData, R.string.video_buffer_size_high, PlaybackEngineController.BUFFER_HIGH, onBufferSelected));
        return OptionCategory.from(VIDEO_BUFFER_ID, videoBufferTitle, optionItems);
    }

    private static OptionItem createVideoBufferOption(Context context, PlayerData playerData, int titleResId, int type, OnBufferSelected onBufferSelected) {
        return UiOptionItem.from(
                context.getString(titleResId),
                optionItem -> {
                    playerData.setVideoBufferType(type);
                    onBufferSelected.onBufferSelected(type);
                },
                playerData.getVideoBufferType() == type);
    }
}

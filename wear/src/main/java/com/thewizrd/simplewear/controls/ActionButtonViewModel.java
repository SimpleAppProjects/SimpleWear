package com.thewizrd.simplewear.controls;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.DNDChoice;
import com.thewizrd.shared_resources.helpers.MultiChoiceAction;
import com.thewizrd.shared_resources.helpers.NormalAction;
import com.thewizrd.shared_resources.helpers.RingerChoice;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.ValueAction;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.simplewear.App;
import com.thewizrd.simplewear.MusicPlayerActivity;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.ValueActionActivity;
import com.thewizrd.simplewear.WearableListenerActivity;

public class ActionButtonViewModel {
    private Action action;
    private @DrawableRes
    int mDrawableID;
    private @ColorRes
    int mButtonBackgroundColor;
    private String mActionLabel;
    private String mStateLabel;

    public Action getAction() {
        return action;
    }

    @DrawableRes
    public int getDrawableID() {
        return mDrawableID;
    }

    @ColorRes
    public int getButtonBackgroundColor() {
        return mButtonBackgroundColor;
    }

    public Actions getActionType() {
        return action.getAction();
    }

    public String getActionLabel() {
        return mActionLabel;
    }

    public String getStateLabel() {
        return mStateLabel;
    }

    public ActionButtonViewModel(@NonNull Action action) {
        this.action = action;
        mDrawableID = R.drawable.ic_cc_clear;
        mButtonBackgroundColor = R.color.buttonDisabled;
        initialize(action);
    }

    private void initialize(Action action) {
        mButtonBackgroundColor = R.color.colorPrimary;
        mDrawableID = R.drawable.ic_cc_clear;

        if (action instanceof ToggleAction) {
            ToggleAction tA = (ToggleAction) action;

            if (!tA.isActionSuccessful()) {
                // Revert state
                tA.setEnabled(!tA.isEnabled());
            }

            mButtonBackgroundColor = tA.isEnabled() ? R.color.buttonEnabled : R.color.buttonDisabled;
            updateIconAndLabel();
        } else if (action instanceof MultiChoiceAction) {
            MultiChoiceAction mA = (MultiChoiceAction) action;

            if (!mA.isActionSuccessful()) {
                // Revert state
                mA.setChoice(mA.getChoice() - 1);
            }

            updateIconAndLabel();
        } else if (action != null) {
            updateIconAndLabel();
        } else {
            throw new IllegalArgumentException("Action class is invalid!!");
        }
    }

    public void onClick(Activity activityContext) {
        action.setActionSuccessful(true);

        if (action instanceof ValueAction) {
            Intent intent = new Intent(activityContext, ValueActionActivity.class)
                    .putExtra(ValueActionActivity.EXTRA_ACTION, getActionType());
            activityContext.startActivityForResult(intent, -1);
        } else if (action instanceof NormalAction && action.getAction() == Actions.MUSICPLAYBACK) {
            Intent intent = new Intent(activityContext, MusicPlayerActivity.class);
            activityContext.startActivityForResult(intent, -1);
        } else {
            if (action instanceof ToggleAction) {
                ToggleAction tA = (ToggleAction) action;
                tA.setEnabled(!tA.isEnabled());
                mButtonBackgroundColor = R.color.colorPrimaryDark;
            } else if (action instanceof MultiChoiceAction) {
                MultiChoiceAction mA = (MultiChoiceAction) action;
                int currentChoice = mA.getChoice();
                int newChoice = currentChoice + 1;
                mA.setChoice(newChoice);
                updateIconAndLabel();
            }

            LocalBroadcastManager.getInstance(activityContext)
                    .sendBroadcast(new Intent(WearableListenerActivity.ACTION_CHANGED)
                            .putExtra(WearableListenerActivity.EXTRA_ACTIONDATA,
                                    JSONParser.serializer(action, Action.class)));
        }
    }

    private void updateIconAndLabel() {
        ToggleAction tA;
        MultiChoiceAction mA;
        Context context = App.getInstance().getAppContext();

        switch (getActionType()) {
            case WIFI:
                tA = (ToggleAction) action;
                mDrawableID = tA.isEnabled() ? R.drawable.ic_network_wifi_white_24dp : R.drawable.ic_signal_wifi_off_white_24dp;
                mActionLabel = context.getString(R.string.action_wifi);
                mStateLabel = tA.isEnabled() ? context.getString(R.string.state_on) : context.getString(R.string.state_off);
                break;
            case BLUETOOTH:
                tA = (ToggleAction) action;
                mDrawableID = tA.isEnabled() ? R.drawable.ic_bluetooth_white_24dp : R.drawable.ic_bluetooth_disabled_white_24dp;
                mActionLabel = context.getString(R.string.action_bt);
                mStateLabel = tA.isEnabled() ? context.getString(R.string.state_on) : context.getString(R.string.state_off);
                break;
            case MOBILEDATA:
                tA = (ToggleAction) action;
                mDrawableID = tA.isEnabled() ? R.drawable.ic_network_cell_white_24dp : R.drawable.ic_signal_cellular_off_white_24dp;
                mActionLabel = context.getString(R.string.action_mobiledata);
                mStateLabel = tA.isEnabled() ? context.getString(R.string.state_on) : context.getString(R.string.state_off);
                break;
            case LOCATION:
                tA = (ToggleAction) action;
                mDrawableID = tA.isEnabled() ? R.drawable.ic_location_on_white_24dp : R.drawable.ic_location_off_white_24dp;
                mActionLabel = context.getString(R.string.action_location);
                mStateLabel = tA.isEnabled() ? context.getString(R.string.state_on) : context.getString(R.string.state_off);
                break;
            case TORCH:
                tA = (ToggleAction) action;
                mDrawableID = R.drawable.ic_lightbulb_outline_white_24dp;
                mActionLabel = context.getString(R.string.action_torch);
                mStateLabel = tA.isEnabled() ? context.getString(R.string.state_on) : context.getString(R.string.state_off);
                break;
            case LOCKSCREEN:
                mDrawableID = R.drawable.ic_lock_outline_white_24dp;
                mActionLabel = context.getString(R.string.action_lockscreen);
                mStateLabel = null;
                break;
            case VOLUME:
                mDrawableID = R.drawable.ic_volume_up_white_24dp;
                mActionLabel = context.getString(R.string.action_volume);
                mStateLabel = null;
                break;
            case DONOTDISTURB:
                mA = (MultiChoiceAction) action;

                mActionLabel = context.getString(R.string.action_dnd);

                DNDChoice dndChoice = DNDChoice.valueOf(mA.getChoice());
                switch (dndChoice) {
                    case OFF:
                        mDrawableID = R.drawable.ic_do_not_disturb_off_white_24dp;
                        mStateLabel = context.getString(R.string.dndstate_off);
                        break;
                    case PRIORITY:
                        mDrawableID = R.drawable.ic_error_white_24dp;
                        mStateLabel = context.getString(R.string.dndstate_priority);
                        break;
                    case ALARMS:
                        mDrawableID = R.drawable.ic_alarm_white_24dp;
                        mStateLabel = context.getString(R.string.dndstate_alarms);
                        break;
                    case SILENCE:
                        mDrawableID = R.drawable.ic_notifications_off_white_24dp;
                        mStateLabel = context.getString(R.string.dndstate_silence);
                        break;
                }
                break;
            case RINGER:
                mA = (MultiChoiceAction) action;

                mActionLabel = context.getString(R.string.action_ringer);

                RingerChoice ringerChoice = RingerChoice.valueOf(mA.getChoice());
                switch (ringerChoice) {
                    case VIBRATION:
                        mDrawableID = R.drawable.ic_vibration_white_24dp;
                        mStateLabel = context.getString(R.string.ringerstate_vib);
                        break;
                    case SOUND:
                        mDrawableID = R.drawable.ic_notifications_active_white_24dp;
                        mStateLabel = context.getString(R.string.ringerstate_sound);
                        break;
                    case SILENT:
                        mDrawableID = R.drawable.ic_volume_off_white_24dp;
                        mStateLabel = context.getString(R.string.ringerstate_silent);
                        break;
                }
                break;
            case MUSICPLAYBACK:
                mDrawableID = R.drawable.ic_play_circle_filled_white_24dp;
                mActionLabel = context.getString(R.string.action_musicplayback);
                mStateLabel = null;
                break;
        }
    }
}

package com.thewizrd.simplewear.sleeptimer;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

public class SelectedPlayerViewModel extends ViewModel {
    private MutableLiveData<String> key = new MutableLiveData<>();

    @NonNull
    public MutableLiveData<String> getKey() {
        return key;
    }

    public String getKeyValue() {
        return key.getValue();
    }

    public void setKey(@Nullable String key) {
        if (!Objects.equals(key, getKey().getValue())) {
            if (Looper.getMainLooper().isCurrentThread()) {
                this.key.setValue(key);
            } else {
                this.key.postValue(key);
            }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        key = null;
    }
}

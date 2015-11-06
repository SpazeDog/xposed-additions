package com.spazedog.xposed.additionsgb.app;


import android.app.Service;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.service.PreferenceService;

public class Instantiator<T> {

    public static final Instantiator<Fragment> Fragments = new Instantiator<Fragment>();
    public static final Instantiator<DialogFragment> Dialogs = new Instantiator<DialogFragment>();
    public static final Instantiator<Service> Services = new Instantiator<Service>();

    protected Class<?> get(int id) {
        switch (id) {
            // Fragments
            case R.id.fragment_launch_selector: return FragmentLaunchSelector.class;
            case R.id.fragment_log: return FragmentLog.class;
            case R.id.fragment_status: return FragmentStatus.class;
            case R.id.fragment_power: return FragmentPower.class;
            case R.id.fragment_settings: return FragmentSettings.class;

            // Services
            case R.id.service_preferences: return PreferenceService.class;

            // Nothing as default
            default: return null;
        }
    }

    protected Instantiator() {}

    public Class<? extends T> getClass(int id) {
        Class<?> clazz = get(id);

        if (clazz != null) {
            try {
                return (Class<? extends T>) clazz;

            } catch (ClassCastException e) {}
        }

        return null;
    }

    public T getInstance(int id) {
        Class<? extends T> clazz = getClass(id);

        if (clazz != null) {
            try {
                return clazz.getDeclaredConstructor().newInstance();

            } catch (Throwable e) {}
        }

        return null;
    }
}

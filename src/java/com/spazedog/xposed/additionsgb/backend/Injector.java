package com.spazedog.xposed.additionsgb.backend;

import android.content.Context;

import com.spazedog.lib.reflecttools.bridge.InitBridge;
import com.spazedog.xposed.additionsgb.backend.service.BackendService;

public class Injector extends InitBridge {

    public static final void initialize() throws Throwable {
        InitBridge.initialize( Injector.class );
    }

    @Override
    public void onZygoteInit() {
		/*
		 * This should be loaded right away to catch any errors
		 */
        LogcatMonitor.init();
    }

    @Override
    public void onSystemInit(Context systemContext) {
		/*
		 * Load all modules
		 */
        BackendService.init(systemContext);
        SystemStateListener.init(systemContext);
    }
}

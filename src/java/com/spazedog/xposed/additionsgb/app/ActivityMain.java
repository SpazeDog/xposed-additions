/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2014 Daniel Bergløv
 *
 * Xposed Additions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Xposed Additions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Xposed Additions. If not, see <http://www.gnu.org/licenses/>
 */

package com.spazedog.xposed.additionsgb.app;

import android.annotation.SuppressLint;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.spazedog.lib.utilsLib.HashBundle;
import com.spazedog.lib.utilsLib.app.MsgActivity;
import com.spazedog.lib.utilsLib.app.MsgFragment;
import com.spazedog.lib.utilsLib.app.MsgFragmentDialog;
import com.spazedog.xposed.additionsgb.R;
import com.spazedog.xposed.additionsgb.app.service.PreferenceServiceMgr;
import com.spazedog.xposed.additionsgb.backend.service.BackendServiceMgr;
import com.spazedog.xposed.additionsgb.utils.Constants;

public class ActivityMain extends MsgActivity implements OnNavigationItemSelectedListener, DrawerListener {

    public static class ActivityMainFragment extends MsgFragment {

        public PreferenceServiceMgr getPreferenceMgr() {
            return ((ActivityMain) getActivity()).mPreferenceMgr;
        }

        public BackendServiceMgr getBackendMgr() {
            return ((ActivityMain) getActivity()).mBackendMgr;
        }
    }

    public static class ActivityMainDialog extends MsgFragmentDialog {

        public PreferenceServiceMgr getPreferenceMgr() {
            return ((ActivityMain) getActivity()).mPreferenceMgr;
        }

        public BackendServiceMgr getBackendMgr() {
            return ((ActivityMain) getActivity()).mBackendMgr;
        }
    }

    protected DrawerLayout mDrawerView;
    protected NavigationView mNavigationView;
    protected Toolbar mToolbarView;
    protected Toolbar mToolbarTopView;
    protected ViewGroup mWrapperView;

    protected PreferenceServiceMgr mPreferenceMgr;
    protected BackendServiceMgr mBackendMgr;

    /*
     * =================================================
     * ACTIVITY OVERRIDES
     */

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_layout);

        mDrawerView = (DrawerLayout) findViewById(R.id.drawer);
        mNavigationView = (NavigationView) findViewById(R.id.navigation);
        mToolbarView = (Toolbar) findViewById(R.id.toolbar);
        // mToolbarTopView = (Toolbar) findViewById(R.id.toolbarTop);
        mWrapperView = (ViewGroup) findViewById(R.id.wrapper);

        if (mToolbarTopView == null) {
            mToolbarTopView = mToolbarView;
        }

        setSupportActionBar(mToolbarView);

        if (mDrawerView != null) {
            if (android.os.Build.VERSION.SDK_INT < 21) {
                mDrawerView.setDrawerShadow(R.drawable.right_shadow, GravityCompat.START);
            }

            mDrawerView.setDrawerListener(this);
        }

        mNavigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mBackendMgr = BackendServiceMgr.getInstance();
        mPreferenceMgr = new PreferenceServiceMgr(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        init();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mPreferenceMgr.close();
        mPreferenceMgr = null;
        mBackendMgr = null;
    }


    /*
     * =================================================
     * DRAWER CONTROL OVERRIDES
     */

    @Override
    public void onDrawerClosed(View arg0) {
        sendMessage(Constants.MSG_NAVIGATION_DRAWER_STATE, "visible", false, true);
    }

    @Override
    public void onDrawerOpened(View arg0) {
        sendMessage(Constants.MSG_NAVIGATION_DRAWER_STATE, "visible", true, true);
    }

    @SuppressLint("NewApi")
    @Override
    public void onDrawerSlide(View drawerView, float slideOffset) {
        if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
            mWrapperView.setTranslationX((slideOffset * drawerView.getWidth()) / 2);
        }
    }

    @Override
    public void onDrawerStateChanged(int arg0) {}

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Fragment fragment = Instantiator.Fragments.getInstance(item.getItemId());

        if (fragment != null) {
            loadFragment(fragment, false);
        }

        return false;
    }

    public boolean isDrawerOpen() {
        return mDrawerView.isDrawerOpen(GravityCompat.START);
    }


    /*
     * =================================================
     * FRAGMENT MANAGEMENT
     */

    public void loadFragment(int id, Bundle args, Boolean backStack) {
        Fragment fragment = Instantiator.Fragments.getInstance(id);

        if (fragment != null) {
            if (args != null) {
                fragment.setArguments(args);
            }

            loadFragment(fragment, backStack);
        }
    }

    public void loadFragment(Fragment fragment, Boolean backStack) {
        FragmentManager manager = getSupportFragmentManager();
        DialogFragment dialog = fragment instanceof DialogFragment ? (DialogFragment) fragment : null;
        String tag = fragment.getClass().getSimpleName();

        if ((!backStack && dialog == null) || manager.findFragmentByTag(tag) == null) {
            if (dialog != null) {
                dialog.show(manager, tag);

            } else {
                FragmentTransaction transaction = manager.beginTransaction();
                transaction.replace(R.id.content, fragment, tag);

                if (backStack) {
                    transaction.addToBackStack(null);

                } else if (manager.getBackStackEntryCount() > 0) {
                    manager.popBackStack(manager.getBackStackEntryAt(0).getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }

                transaction.commit();
            }
        }

        if (mDrawerView != null) {
            mDrawerView.closeDrawers();
        }
    }

    public Fragment getFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.content);
    }

    public boolean isFragmentLoaded(Fragment fragment) {
        return isFragmentLoaded(fragment.getClass());
    }

    public boolean isFragmentLoaded(Class<? extends Fragment> fragment) {
        return getSupportFragmentManager().findFragmentByTag(fragment.getSimpleName()) != null;
    }


    /*
     * =================================================
     * CONTENT MANAGEMENT
     */

    protected void init() {
        FragmentManager fragmentManager = getSupportFragmentManager();

        if (fragmentManager.findFragmentById(R.id.content) == null) {
            Menu navigationMenu = mNavigationView.getMenu();

            if (navigationMenu.size() > 0) {
                onNavigationItemSelected(navigationMenu.getItem(0));
            }
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        if (mToolbarView != null) {
            mToolbarView.setTitle(title);
        }
    }

    protected void onNavigationChange() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Menu navigationMenu = mNavigationView.getMenu();

        for (int i=0; i < navigationMenu.size(); i++) {
            MenuItem item = navigationMenu.getItem(i);
            boolean isLoaded = isFragmentLoaded(Instantiator.Fragments.getClass(item.getItemId()));

            if (isLoaded) {
                item.setChecked(true);
                setTitle(item.getTitle());

                break;
            }
        }

        if (fragmentManager.getBackStackEntryCount() > 0) {
            mToolbarTopView.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
            mToolbarTopView.setNavigationOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    FragmentManager manager = getSupportFragmentManager();
                    manager.popBackStack();
                }
            });

        } else if (mDrawerView != null) {
            mToolbarTopView.setNavigationIcon(R.drawable.ic_menu_white_24dp);
            mToolbarTopView.setNavigationOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mDrawerView.isDrawerOpen(GravityCompat.START)) {
                        mDrawerView.closeDrawer(GravityCompat.START);

                    } else {
                        mDrawerView.openDrawer(GravityCompat.START);
                    }
                }
            });

        } else {
            mToolbarTopView.setNavigationIcon(null);
            mToolbarTopView.setNavigationOnClickListener(null);
        }
    }


    /*
     * =================================================
     * MESSAGE HANDLING
     */

    @Override
    public void onReceiveMessage(int type, HashBundle data, boolean isSticky) {
        super.onReceiveMessage(type, data, isSticky);

        switch (type) {
            case Constants.MSG_BACKSTACK_CHANGE:
            case Constants.MSG_FRAGMENT_ATTACHMENT:

                onNavigationChange();
        }
    }
}

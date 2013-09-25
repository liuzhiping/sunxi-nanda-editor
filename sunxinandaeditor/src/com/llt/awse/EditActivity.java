/*
 * Copyright 2013 Bartosz Jankowski
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.llt.awse;

import java.util.Locale;

import com.spazedog.lib.rootfw3.RootFW;
import com.spazedog.lib.rootfw3.extenders.FileExtender.File;
import com.spazedog.lib.rootfw3.extenders.FilesystemExtender.Device;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

//import com.spazedog.lib.rootfw3.RootFW;


public class EditActivity extends FragmentActivity implements ActionBar.TabListener {


    SectionsPagerAdapter mSectionsPagerAdapter;
    ViewPager mViewPager;
    int nSections = 1;

    final String TAG = "AW-SE";
    final ActionBar.TabListener mTabListener = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }
        

        if( !Build.HARDWARE.equals("sun4i") && !Build.HARDWARE.equals("sun5i") && 
        		!Build.HARDWARE.equals("sun6i") && !Build.HARDWARE.equals("sun7i")) 
        {
        	Log.e(TAG, "Unknown hardware '" + Build.HARDWARE + "' ! Are you its Allwinner?");
        	final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle("Error!");
			alertDialog.setMessage("Unknown hardware detected. Further actions may damage device.\nDo you want to continue?");
			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Yes", new DialogInterface.OnClickListener() {
			   @Override
			public void onClick(DialogInterface dialog, int which) {
			     alertDialog.dismiss();
			   }
			});
			alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Nope!", new DialogInterface.OnClickListener() {
				   @Override
				public void onClick(DialogInterface dialog, int which) {
				     alertDialog.dismiss();
				     finish();
				   }
				});
			alertDialog.show();	
        }
        
// Check root access...
        final RootFW root = new RootFW(true);

        if (root.connect()) {
            if(!root.isRoot())
            {
            	final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
    			alertDialog.setTitle("Error!");
    			alertDialog.setMessage("App needs root access to work!");
    			alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
    			   @Override
    			public void onClick(DialogInterface dialog, int which) {
    			     alertDialog.dismiss();
    			     finish();
    			   }
    			});
    			alertDialog.show();	
            }
            // Try to remount root dir\=
            if(root.filesystem("rootfs").addMount("/", new String[] {"rw","remount"}))
            {
            	Log.i(TAG,"Succesfully remounted root dir!");
            }
            
            //Create AWeSomE temp directory
            
            File f = root.file("/mnt/awse");
            f.createDirectory();
            	Log.v(TAG,"Trying to mount bootloader partition...");
            	//int res = root.shell().run("busybox mount -oro,loop -tvfat /dev/block/nanda /mnt/awse").getResultCode();
            	if(root.filesystem("/dev/block/nanda").addMount("/mnt/awse","vfat",new String[] {"loop"}))
            		Log.v(TAG, "Successfully mounted!");
            	else if(root.filesystem("/dev/block/nanda").isMounted())
            		Log.v(TAG, "Device is already mounted!");
            	else
            	{
                	final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        			alertDialog.setTitle("Error!");
        			alertDialog.setMessage("Couldn't mount the device. Try to update su binary (Download SuperSU or SuperUser)!");
        			alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
        			   @Override
        			public void onClick(DialogInterface dialog, int which) {
        			     alertDialog.dismiss();
        			     //Use rmdir to prevent from important files removal!
        			     root.shell().run("rmdir /mnt/awse");
        			     finish();
        			   }
        			});
        			alertDialog.show();	
            	}
        }
    }

    @Override
	protected void onDestroy() {
		// Clean up mounted point
    	
        final RootFW root = new RootFW(true);

        if (root.connect()) {
            if(root.isRoot())
            {
            	if(root.filesystem("/dev/block/nanda").isMounted())
            	{
            		Log.v(TAG, "Unmounting device...");
            		if(root.filesystem("/dev/block/nanda").removeMount())
            		//Use rmdir to prevent from important files removal in case of unmount failure!
   			     	root.shell().run("rmdir /mnt/awse");
            	}
            }
        }
		super.onDestroy();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.edit, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item)
    {
        switch(item.getItemId()) {
            case R.id.menu_exit:
                finish();
                break;
            case R.id.menu_reload:
                break;
        }
        return super.onMenuItemSelected(featureId, item);
    }


    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    public void lAddTab()
    {
        final ActionBar actionBar = getActionBar();
        actionBar.addTab(
                actionBar.newTab()
                        .setText(mSectionsPagerAdapter.getPageTitle(nSections))
                        .setTabListener(mTabListener));
        ++nSections;
        mSectionsPagerAdapter.notifyDataSetChanged();

    }

    
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
    	
        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }


        @Override
        public Fragment getItem(int position) {
        	Fragment fragment = null;
        	
    		fragment = new TabSectionFragment();
    		Bundle args = new Bundle();
    		args.putInt(TabSectionFragment.ARG_SECTION_NUMBER, position + 1);
    		fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return nSections;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.main_section);
                case 1:
                    return "General";
                default:
                	return ""+ position;
            }
        }
    }

    /**
     * A dummy fragment representing a section of the app, but that simply
     * displays dummy text.
     */
    public static class TabSectionFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        public static final String ARG_SECTION_NUMBER = "section_number";

        public TabSectionFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
        	int nSection = getArguments().getInt(ARG_SECTION_NUMBER);
        	View rootView = null;
        	switch(nSection)
        	{
        		case 1:
        			rootView = inflater.inflate(R.layout.fragment_config_entry_text, container, false);
        		break;
        		default:
        			rootView = inflater.inflate(R.layout.fragment_edit_dummy, container, false);
        			TextView dummyTextView = (TextView) rootView.findViewById(R.id.section_label);
        			dummyTextView.setText(""+ nSection);
        	}
            return rootView;
        }
    }

}
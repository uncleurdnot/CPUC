/*
Copyright (c) 2013, California State University Monterey Bay (CSUMB).
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    1. Redistributions of source code must retain the above copyright notice,
       this list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above
           copyright notice, this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.

    3. Neither the name of the CPUC, CSU Monterey Bay, nor the names of
       its contributors may be used to endorse or promote products derived from
       this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package gov.ca.cpuc.calspeed.android;

import gov.ca.cpuc.calspeed.android.R;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.internal.app.ActionBarWrapper;
import com.actionbarsherlock.view.MenuItem;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NavUtils;
import android.util.Log;

public class DisplayInfo extends SherlockFragmentActivity {
     
	Fragment advertisedFixedFragment;  
	Fragment advertisedMobileFragment; 
	Fragment estimatedMobileFragment;
	Fragment advertisedSatelliteFragment;

	ActionBar.Tab advertisedFixedTab;
	ActionBar.Tab advertisedMobileTab;
	ActionBar.Tab estimatedMobileTab;
	ActionBar.Tab advertisedSatelliteTab;

	ActionBar actionbar;

	Intent myIntent;
	double x;    // hold "x" coordinate value from ArcGIS service
	double y;    // hold "y" coordinate value from ArcGIS service
	final int spatial = 102113;
	String address;

	private Intent exceptionIntent;
	
	private boolean isPaused = false;
	AdvertisedDataResponse adverDataRespHandler;
	
	public static void setHasEmbeddedTabs(Object inActionBar, final boolean inHasEmbeddedTabs)
    {
        // get the ActionBar class
        Class<?> actionBarClass = inActionBar.getClass();

        // if it is a Jelly Bean implementation (ActionBarImplJB), get the super class (ActionBarImplICS)
        if ("android.support.v7.app.ActionBarImplJB".equals(actionBarClass.getName()))
        {
                actionBarClass = actionBarClass.getSuperclass();
        }

        try
        {
                // try to get the mActionBar field, because the current ActionBar is probably just a wrapper Class
                // if this fails, no worries, this will be an instance of the native ActionBar class or from the ActionBarImplBase class
                final Field actionBarField = actionBarClass.getDeclaredField("mActionBar");
                actionBarField.setAccessible(true);
                inActionBar = actionBarField.get(inActionBar);
                actionBarClass = inActionBar.getClass();
        }
        catch (IllegalAccessException e) {}
        catch (IllegalArgumentException e) {}
        catch (NoSuchFieldException e) {}

        try
        {
                // now call the method setHasEmbeddedTabs, this will put the tabs inside the ActionBar
                // if this fails, you're on you own <img src="http://www.blogc.at/wp-includes/images/smilies/icon_wink.gif" alt=";-)" class="wp-smiley">
                final Method method = actionBarClass.getDeclaredMethod("setHasEmbeddedTabs", new Class[] { Boolean.TYPE });
                method.setAccessible(true);
                method.invoke(inActionBar, new Object[]{ inHasEmbeddedTabs });
        }
        catch (NoSuchMethodException e)        {}
        catch (InvocationTargetException e) {}
        catch (IllegalAccessException e) {}
        catch (IllegalArgumentException e) {}
    }
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// For Debugging
		Log.w("Viewer", "DisplayInfo - onCreate()");
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.displayinfo);
		
		exceptionIntent = new Intent(this, MainActivity.class);
        
		myIntent = getIntent(); 
		address = myIntent.getStringExtra("address");
		x = myIntent.getDoubleExtra("x", 0); 
		y = myIntent.getDoubleExtra("y", 0); 

		actionbar = getSupportActionBar();
		
		// Tell the ActionBar we want to use Tabs.
		actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		setHasEmbeddedTabs(actionbar, false);
		
		// To enable "Up" (or "Back") function when a user clicks the app icon.
		actionbar.setDisplayHomeAsUpEnabled(true);

		
		// Initializing the tabs.
		// FIXME: For a while, we do not use the customized tabs.
		advertisedFixedTab = actionbar.newTab().setText("Advertised Fixed");
		advertisedMobileTab = actionbar.newTab().setText("Advertised Mobile");
		estimatedMobileTab = actionbar.newTab().setText("Estimated Mobile");		
		advertisedSatelliteTab = actionbar.newTab().setText("Advertised Satellite");

		// URL for advertised information.
		String advertisedURL = "http://cpuc.cloudapp.net/ArcGIS/rest/services/MOBILE_VIEWER_APP_mod/MapServer/0/query?text=&geometry="
				+ x
				+ "%2C"
				+ y
				+ "&geometryType=esriGeometryPoint&inSR="
				+ spatial
				+ "&spatialRel=esriSpatialRelIntersects&"
				+ "relationParam=&objectIds=&where=&time=&returnCountOnly=false&returnIdsOnly=false&returnGeometry=false&maxAllowable"
				+ "Offset=&outSR="
				+ spatial
				+ "&outFields=*&f=pjson";

		final ProgressDialog dialog = new ProgressDialog(DisplayInfo.this);
		adverDataRespHandler = new AdvertisedDataResponse(DisplayInfo.this, dialog);
		adverDataRespHandler.execute(advertisedURL);
		
		// 5 seconds timeout to get the advertised data.
		Handler handler2 = new Handler();
		handler2.postDelayed(new Runnable()
		{
			@Override
			public void run() {
				if ( adverDataRespHandler.isCancelled() && !isFinishing() && !isPaused)
				{
					dialog.dismiss();
					AlertDialog.Builder builder = new AlertDialog.Builder(DisplayInfo.this);
			        builder.setTitle("Website Down")
					.setMessage("Sorry, the Viewer website is down, please try again later.")
			        .setCancelable(false)
			        .setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
			            public void onClick(DialogInterface dialog, int id) {
			                dialog.dismiss();
			                startActivity(exceptionIntent);
			                finish();
			            }
			        });
			        AlertDialog alert = builder.create();
			        alert.show();
				}
			}
		}, 5000 );

		// 30 seconds timeout to get the advertised data.
		Handler handler = new Handler();
		handler.postDelayed(new Runnable()
		{
			@Override
			public void run() {
				if ( adverDataRespHandler.getStatus() == AsyncTask.Status.RUNNING && !isFinishing())
				{
					adverDataRespHandler.closeDownloadDialog();
					adverDataRespHandler.cancel(true);
					dialog.dismiss();
					
					AlertDialog.Builder builder = new AlertDialog.Builder(DisplayInfo.this);
			        builder.setTitle("Website Down")
					.setMessage("Sorry, the Viewer website is down. Please try again later.")
			        .setCancelable(false)
			        .setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
			            public void onClick(DialogInterface dialog, int id) {
			                dialog.dismiss();
			                startActivity(exceptionIntent);
			                finish();
			            }
			        });
			        AlertDialog alert = builder.create();
			        alert.show();
				}
			}
		}, 30000 );		
		
		
		adverDataRespHandler.addObserver(new AdvertisedDataResponse.Callback() {
			@Override
			public void onFailure() {
 			}

			@Override
			public void onComplete(AdvertisedData datas) {
				if(isPaused) {
					adverDataRespHandler.cancel(true);
					startActivity(exceptionIntent);
			        finish();
			        return;
				}
				
				advertisedMobileFragment = new AdvertisedMobileFragment(datas, address);				
				advertisedFixedFragment = new AdvertisedFixedFragment(datas, address);
				advertisedSatelliteFragment = new AdvertisedSatelliteFragment(datas, address);
								
				advertisedMobileTab.setTabListener(new MyTabsListener(advertisedMobileFragment));
				advertisedFixedTab.setTabListener(new MyTabsListener(advertisedFixedFragment));
				advertisedSatelliteTab.setTabListener(new MyTabsListener(advertisedSatelliteFragment));

				actionbar.addTab(advertisedFixedTab, 0, true);
				actionbar.addTab(advertisedMobileTab, 1, false);
				actionbar.addTab(advertisedSatelliteTab, 2, false);
				
				
				// URL for estimated information.
				String estimatedURL = "http://cpuc.cloudapp.net/ArcGIS/rest/services/MOBILE_VIEWER_APP_mod/MapServer/1/query?text=&geometry="
						+ x
						+ "%2C"
						+ y
						+ "&geometryType=esriGeometryPoint&inSR="
						+ spatial
						+ "&spatialRel=esriSpatialRelIntersects&"
						+ "relationParam=&objectIds=&where=&time=&returnCountOnly=false&returnIdsOnly=false&returnGeometry=false&maxAllowable"
						+ "Offset=&outSR="
						+ spatial
						+ "&outFields=*&f=pjson";

				EstimatedDataResponse estimatedDataR = new EstimatedDataResponse(DisplayInfo.this);
				estimatedDataR.execute(estimatedURL);
				estimatedDataR.addObserver(new EstimatedDataResponse.Callback() {
					@Override
					public void onFailure() {
 					}

					@Override
					public void onComplete(EstimatedData datas) {
						estimatedMobileFragment = new EstimatedMobileFragment(datas, address);
						
						estimatedMobileTab.setTabListener(new MyTabsListener(estimatedMobileFragment));
						actionbar.addTab(estimatedMobileTab, 2, false);

					}
				});
				
			}
		});	

	}
	

	@Override
    protected void onPause() {
		isPaused = true;
//		adverDataRespHandler.cancel(true);
//		startActivity(exceptionIntent);
//        finish();
        super.onPause();
    }
	
	@Override
    protected void onResume() {
		isPaused = false;
        super.onResume();
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		// For debugging
		Log.w("Viewer", "DisplayInfo - onOptionsItemSelected()");
		
	    switch (item.getItemId()) {
	    // Respond to the action bar's Up/Home button
	    case android.R.id.home:
	        NavUtils.navigateUpFromSameTask(this);
	        return true;
	    }
	    return super.onOptionsItemSelected(item);
	}
	
	class MyTabsListener implements ActionBar.TabListener {
		public Fragment fragment;

		public MyTabsListener(Fragment fragment) {
			this.fragment = fragment;
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {
		}

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			ft.replace(R.id.fragment_container, fragment);
		}

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			ft.remove(fragment);
		}
	}

}

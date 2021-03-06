package de.robv.android.xposed.installer;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ModulesFragment extends ListFragment {
	public static final String SETTINGS_CATEGORY = "de.robv.android.xposed.category.MODULE_SETTINGS";
	private Set<String> enabledModules;
	private String installedXposedVersion;
	ModuleAdapter modules;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
        
		installedXposedVersion = InstallerFragment.getJarInstalledVersion(null);
		
        modules = new ModuleAdapter(getActivity());
        enabledModules = PackageChangeReceiver.getEnabledModules(getActivity());
        
		PackageManager pm = getActivity().getPackageManager();
		for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_META_DATA)) {
			ApplicationInfo app = pkg.applicationInfo;
			if (app.metaData == null || !app.metaData.containsKey("xposedmodule"))
				continue;
			
			String minVersion = app.metaData.getString("xposedminversion");
			String description = app.metaData.getString("xposeddescription", "");
			if (description.length() == 0) {
				// Check if the metadata is using a resource and load it if so
				try {
					int resId = app.metaData.getInt("xposeddescription", 0);
					if (resId != 0) {
						description = pm.getResourcesForApplication(app).getString(resId);
					}
				} catch (Exception e) { }
			}
			modules.add(new XposedModule(pkg.packageName, pkg.versionName, pm.getApplicationLabel(app).toString(),
					pm.getApplicationIcon(app), minVersion, description));
		}
		
		modules.sort(new Comparator<XposedModule>() {
			@Override
			public int compare(XposedModule lhs, XposedModule rhs) {
				return lhs.appName.compareTo(rhs.appName);
			}
		});
        
        setListAdapter(modules);
        setEmptyText(getActivity().getString(R.string.no_xposed_modules_found));

        getListView().setFastScrollEnabled(true);

		getListView().setDivider(new ColorDrawable(0xFF0099cc));
		getListView().setDividerHeight(1);
		registerForContextMenu(getListView());
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        // Inflate the menu; this adds items to the action bar if it is present.
		menu.setHeaderTitle(modules.getItem(((AdapterView.AdapterContextMenuInfo) menuInfo).position).getAppName());
        getActivity().getMenuInflater().inflate(R.menu.modules_menu, menu);		
    }
	@Override 
    public boolean onContextItemSelected(MenuItem item){  
		AdapterView.AdapterContextMenuInfo module = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		String packageName = modules.getItem(module.position).getPackageName();
		Intent uninstallIntent,infoIntent;
		switch (item.getItemId()) {
		case R.id.configuration:
			infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			Uri uri = Uri.fromParts("package", packageName, null);
	        infoIntent.setData(uri);
			startActivity(infoIntent);
			break;
		case R.id.uninstall_module:
			uninstallIntent = new Intent(Intent.ACTION_DELETE);
		    uninstallIntent.setData(Uri.parse("package:" + packageName));
		    startActivity(uninstallIntent);
			break;

		default:
			break;
		}
		
		return true ;
    }
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		String packageName = (String) v.getTag();
		Intent launchIntent = getSettingsIntent(packageName);
		if (launchIntent != null)
			startActivity(launchIntent);
		else
			Toast.makeText(getActivity(), getActivity().getString(R.string.module_no_ui), Toast.LENGTH_LONG).show();
	}

	private Intent getSettingsIntent(String packageName) {
		// taken from ApplicationPackageManager.getLaunchIntentForPackage(String)
		// first looks for an Xposed-specific category, falls back to getLaunchIntentForPackage
		PackageManager pm = getActivity().getPackageManager();

		Intent intentToResolve = new Intent(Intent.ACTION_MAIN);
		intentToResolve.addCategory(SETTINGS_CATEGORY);
		intentToResolve.setPackage(packageName);
		List<ResolveInfo> ris = pm.queryIntentActivities(intentToResolve, 0);

		if (ris == null || ris.size() <= 0) {
			return pm.getLaunchIntentForPackage(packageName);
		}

		Intent intent = new Intent(intentToResolve);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setClassName(ris.get(0).activityInfo.packageName, ris.get(0).activityInfo.name);
		return intent;
	}

    private class ModuleAdapter extends ArrayAdapter<XposedModule> {
		public ModuleAdapter(Context context) {
			super(context, R.layout.list_item_module, R.id.text);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
		 	View view = super.getView(position, convertView, parent);

			if (convertView == null) {
				// The reusable view was created for the first time, set up the listener on the checkbox
				((CheckBox) view.findViewById(R.id.checkbox)).setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						String packageName = (String) buttonView.getTag();
						boolean changed = enabledModules.contains(packageName) ^ isChecked;
						if (changed) {
							synchronized (enabledModules) {
								if (isChecked)
									enabledModules.add(packageName);
								else
									enabledModules.remove(packageName);
							}

							PackageChangeReceiver.setEnabledModules(getContext(), enabledModules);
							PackageChangeReceiver.updateModulesList(getContext(), enabledModules);
						}
					}
				});
			}

			XposedModule item = getItem(position);
			// Store the package name in some views' tag for later access
			((CheckBox) view.findViewById(R.id.checkbox)).setTag(item.packageName);
			view.setTag(item.packageName);

			((ImageView) view.findViewById(R.id.icon)).setImageDrawable(item.icon);

			TextView descriptionText = (TextView) view.findViewById(R.id.description);
			if (item.description.length() > 0) {
				descriptionText.setText(item.description);
				descriptionText.setTextColor(0xFF777777);
			} else {
				descriptionText.setText(getActivity().getString(R.string.module_empty_description));
				descriptionText.setTextColor(0xFFCC7700);
			}

			CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
			checkbox.setChecked(enabledModules.contains(item.packageName));
			TextView warningText = (TextView) view.findViewById(R.id.warning);

			if (item.minVersion == null) {
				checkbox.setEnabled(false);
				warningText.setText(getString(R.string.no_min_version_specified));
				warningText.setVisibility(View.VISIBLE);
            } else if (installedXposedVersion != null && PackageChangeReceiver.compareVersions(item.minVersion, installedXposedVersion) > 0) {
            	checkbox.setEnabled(false);
            	warningText.setText(String.format(getString(R.string.warning_xposed_min_version), 
            			PackageChangeReceiver.trimVersion(item.minVersion)));
            	warningText.setVisibility(View.VISIBLE);
            } else if (PackageChangeReceiver.compareVersions(item.minVersion, PackageChangeReceiver.MIN_MODULE_VERSION) < 0) {
            	checkbox.setEnabled(false);
            	warningText.setText(String.format(getString(R.string.warning_min_version_too_low), 
            			PackageChangeReceiver.trimVersion(item.minVersion), PackageChangeReceiver.MIN_MODULE_VERSION));
            	warningText.setVisibility(View.VISIBLE);
            } else {
            	checkbox.setEnabled(true);
            	warningText.setVisibility(View.GONE);
            }
            return view;
		}
    	
	}

	private static class XposedModule {
		String packageName;
		String moduleVersion;
		String appName;
		Drawable icon;
		String minVersion;
		String description;

		public XposedModule(String packageName, String moduleVersion, String appName, Drawable icon, String minVersion, String description) {
			this.packageName = packageName;
			this.moduleVersion = moduleVersion;
			this.appName = appName;
			this.icon = icon;
			this.minVersion = minVersion;
			this.description = description.trim();
		}

		@Override
		public String toString() {
			return String.format("%s [%s]", appName, moduleVersion);
		}
		public String getPackageName(){
			return packageName;
		}
		public String getAppName(){
			return appName;
		}
	}
}

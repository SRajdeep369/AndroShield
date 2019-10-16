package com.example.rajdeep.firewall;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;
import java.util.Comparator;
import java.util.*;
import static com.example.rajdeep.firewall.Api.getApps;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {

    // Menu options
    private static final int MENU_DISABLE = 0;
    private static final int MENU_APPLY = 2;

    /** progress dialog instance */
    private ListView listview;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        checkPreferences();
        setContentView(R.layout.main);
        this.findViewById(R.id.label_mode).setOnClickListener(this);
        Api.assertBinaries(this, true);
        showOrLoadApplications();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Force re-loading the application list
        Log.d("Androwall", "onStart() - Forcing APP list reload!");
        Api.applications = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.listview == null) {
            this.listview = (ListView) this.findViewById(R.id.listview);
        }
        refreshHeader();
        showOrLoadApplications();

    }

    @Override
    protected void onPause() {
        super.onPause();
        this.listview.setAdapter(null);
    }

    /**
     * Check if the stored preferences are OK
     */
    private void checkPreferences() {
        final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (prefs.getString(Api.PREF_MODE, "").length() == 0) {
            editor.putString(Api.PREF_MODE, Api.MODE_WHITELIST);
            changed = true;
        }
		/** delete the old preference names */
        if (prefs.contains("AllowedUids")) {
            editor.remove("AllowedUids");
            changed = true;
        }
        if (prefs.contains("Interfaces")) {
            editor.remove("Interfaces");
            changed = true;
        }
        if (changed)
            editor.commit();
    }

    /**
     * Refresh informative header
     */
    private void refreshHeader() {
        final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
        final String mode = prefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
        final TextView labelmode = (TextView) this
                .findViewById(R.id.label_mode);
        final Resources res = getResources();
        int resid = (mode.equals(Api.MODE_WHITELIST) ? R.string.mode_whitelist
                : R.string.mode_blacklist);
        labelmode.setText(res.getString(R.string.mode_header,
                res.getString(resid)));
        resid = (Api.isEnabled(this) ? R.string.title_enabled
                : R.string.title_disabled);
        setTitle(res.getString(resid, Api.VERSION));
    }

    /**
     * Displays a dialog box to select the operation mode (black or white list)
     */
    private void selectMode() {
        final Resources res = getResources();
        new AlertDialog.Builder(this)
                .setItems(
                        new String[] { res.getString(R.string.mode_whitelist),
                                res.getString(R.string.mode_blacklist) },
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                final String mode = (which == 0 ? Api.MODE_WHITELIST
                                        : Api.MODE_BLACKLIST);
                                final SharedPreferences.Editor editor = getSharedPreferences(
                                        Api.PREFS_NAME, 0).edit();
                                editor.putString(Api.PREF_MODE, mode);
                                editor.commit();
                                refreshHeader();
                            }
                        }).setTitle("Select mode:").show();
    }



    /**
     * If the applications are cached, just show them, otherwise load and show
     */
    private void showOrLoadApplications() {
        final Resources res = getResources();
        if (Api.applications == null) {
            // The applications are not cached.. so lets display the progress
            // dialog
            final ProgressDialog progress = ProgressDialog.show(this,
                    res.getString(R.string.working),
                    res.getString(R.string.reading_apps), true);
            final Handler handler = new Handler() {
                public void handleMessage(Message msg) {
                    try {
                        progress.dismiss();
                    } catch (Exception ex) {
                    }
                    showApplications();
                }
            };
            new Thread() {
                public void run() {
                    getApps(MainActivity.this);
                    handler.sendEmptyMessage(0);
                }
            }.start();
        } else {
            // the applications are cached, just show the list
            showApplications();
        }
    }
    /**
     * Show the list of applications
     */
    private void showApplications() {
        final Api.DroidApp[] apps = getApps(this);
        // Sort applications - selected first, then alphabetically
        Arrays.sort(apps, new Comparator<Api.DroidApp>() {
            public int compare(Api.DroidApp o1, Api.DroidApp o2) {
                if ((o1.selected_wifi | o1.selected_3g) == (o2.selected_wifi | o2.selected_3g)) {
                    return o1.names[0].compareTo(o2.names[0]);
                }
                if (o1.selected_wifi || o1.selected_3g)
                    return -1;
                return 1;
            }
        });
        final LayoutInflater inflater = getLayoutInflater();
        final ListAdapter adapter = new ArrayAdapter<Api.DroidApp>(this,
                R.layout.listitem, R.id.itemtext, apps) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ListEntry entry;
                if (convertView == null) {
                    // Inflate a new view
                    convertView = inflater.inflate(R.layout.listitem, parent,
                            false);
                    entry = new ListEntry();
                    entry.box_wifi = (CheckBox) convertView
                            .findViewById(R.id.itemcheck_wifi);
                    entry.box_3g = (CheckBox) convertView
                            .findViewById(R.id.itemcheck_3g);
                    entry.text = (TextView) convertView
                            .findViewById(R.id.itemtext);
                    convertView.setTag(entry);
                    entry.box_wifi
                            .setOnCheckedChangeListener(MainActivity.this);
                    entry.box_3g.setOnCheckedChangeListener(MainActivity.this);
                } else {
                    // Convert an existing view
                    entry = (ListEntry) convertView.getTag();
                }
                final Api.DroidApp app = apps[position];
                entry.text.setText(app.toString());
                final CheckBox box_wifi = entry.box_wifi;
                box_wifi.setTag(app);
                box_wifi.setChecked(app.selected_wifi);
                final CheckBox box_3g = entry.box_3g;
                box_3g.setTag(app);
                box_3g.setChecked(app.selected_3g);
                return convertView;
            }
        };
        this.listview.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_DISABLE, 0, R.string.fw_enabled);
        menu.add(0, MENU_APPLY, 0, R.string.applyrules);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem item_onoff = menu.getItem(MENU_DISABLE);
        final boolean enabled = Api.isEnabled(this);
        if (enabled) {
            item_onoff.setTitle(R.string.fw_enabled);
        } else {
            item_onoff.setTitle(R.string.fw_disabled);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DISABLE:
                disableOrEnable();
                return true;
            case MENU_APPLY:
                applyOrSaveRules();
                return true;
        }
        return false;
    }

    /**
     * Enables or disables the firewall
     */
    private void disableOrEnable() {
        final boolean enabled = !Api.isEnabled(this);
        Log.d("Androwall", "Changing enabled status to: " + enabled);
        Api.setEnabled(this, enabled);
        if (enabled) {
            applyOrSaveRules();
        } else {
            purgeRules();
        }
        refreshHeader();
    }

    /**
     * Apply or save iptable rules, showing a visual indication
     */
    private void applyOrSaveRules() {
        final Resources res = getResources();
        final boolean enabled = Api.isEnabled(this);
        final ProgressDialog progress = ProgressDialog.show(this, res
                .getString(R.string.working), res
                .getString(enabled ? R.string.applying_rules
                        : R.string.saving_rules), true);
        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                try {
                    progress.dismiss();
                } catch (Exception ex) {
                }
                if (enabled) {
                    Log.d("Androwall", "Applying rules.");
                    if (Api.hasRootAccess(MainActivity.this, true)
                            && Api.applyIptablesRules(MainActivity.this, true)) {
                        Toast.makeText(MainActivity.this,
                                R.string.rules_applied, Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        Log.d("Androwall", "Failed - Disabling firewall.");
                        Api.setEnabled(MainActivity.this, false);
                    }
                } else {
                    Log.d("Androwall", "Saving rules.");
                    Api.saveRules(MainActivity.this);
                    Toast.makeText(MainActivity.this, R.string.rules_saved,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        handler.sendEmptyMessageDelayed(0, 100);
    }

    /**
     * Purge iptable rules, showing a visual indication
     */
    private void purgeRules() {
        final Resources res = getResources();
        final ProgressDialog progress = ProgressDialog.show(this,
                res.getString(R.string.working),
                res.getString(R.string.deleting_rules), true);
        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                try {
                    progress.dismiss();
                } catch (Exception ex) {
                }
                if (!Api.hasRootAccess(MainActivity.this, true))
                    return;
                if (Api.purgeIptables(MainActivity.this, true)) {
                    Toast.makeText(MainActivity.this, R.string.rules_deleted,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
        handler.sendEmptyMessageDelayed(0, 100);
    }

    /**
     * Called an application is check/unchecked
     */
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final Api.DroidApp app = (Api.DroidApp) buttonView.getTag();
        if (app != null) {
            switch (buttonView.getId()) {
                case R.id.itemcheck_wifi:
                    app.selected_wifi = isChecked;
                    break;
                case R.id.itemcheck_3g:
                    app.selected_3g = isChecked;
                    break;
            }
        }
    }

    private static class ListEntry {
        private CheckBox box_wifi;
        private CheckBox box_3g;
        private TextView text;
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.label_mode:
                selectMode();
                break;
        }
    }
}

package net.mobilewebprint.nan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // When the home button is pressed, take the user back to the VisualizerActivity
        if (id == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this);
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements
            SharedPreferences.OnSharedPreferenceChangeListener, OnPreferenceChangeListener {
        EditTextPreference passphrase;
        String currentEncryptType;
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            PreferenceScreen prefScreen = getPreferenceScreen();
            Preference name = findPreference(getString(R.string.service_name));
            Preference info = findPreference(getString(R.string.service_specific_info));
            Preference pass = findPreference(getString(R.string.security_pass));
            name.setOnPreferenceChangeListener(this);
            info.setOnPreferenceChangeListener(this);
            pass.setOnPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Figure out which preference was changed
            Preference preference = findPreference(key);

            String secType = getString(R.string.encryptType);
            String type = sharedPreferences.getString(key, "");
            if (key.equals(secType)) {
                if (type.equals("pmk")) {
                    passphrase.setVisible(true);
                    passphrase.setText("123456789abcdef0123456789abcdef0");
                } else if (type.equals("psk")) {
                    passphrase.setVisible(true);
                    passphrase.setText("12345678");
                } else {
                    passphrase.setVisible(false);
                }
            }
            if (null != preference) {
                // Updates the summary for the preference
                if (!(preference instanceof EditTextPreference)) {
                    String value = sharedPreferences.getString(preference.getKey(), "");
                }
            }



        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            Toast error = Toast.makeText(getContext(), "Please enter non empty string", Toast.LENGTH_LONG);
            String namekey = getString(R.string.service_name);
            String infokey = getString(R.string.service_specific_info);
            String passkey = getString(R.string.security_pass);
            if (preference.getKey().equals(namekey)||preference.getKey().equals(infokey)) {
                Log.d("prefs","IN HERE ");
                String name = (String) newValue;
                if (name.isEmpty()) {
                    error.show();
                    return false;
                }
                else
                    return true;
            }
            return true;
        }

/*        private void setPreferenceSummary(Preference preference, String value) {
            if (preference instanceof ListPreference) {
                // For list preferences, figure out the label of the selected value
                ListPreference listPreference = (ListPreference) preference;
                int prefIndex = listPreference.findIndexOfValue(value);
                if (prefIndex >= 0) {
                    // Set the summary to that label
                    listPreference.setSummary(listPreference.getEntries()[prefIndex]);
                }
            } else if (preference instanceof EditTextPreference) {
                // For EditTextPreferences, set the summary to the value's simple string representation.
                preference.setSummary(value);
            }
        }*/


        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            String secType = getString(R.string.encryptType);
            passphrase = (EditTextPreference) getPreferenceManager().findPreference(getResources().getString(R.string.security_pass));
            currentEncryptType = getPreferenceManager().getSharedPreferences().getString(secType,"");
            Log.d("prefs","TYPE "+currentEncryptType);
            if (currentEncryptType.equals("open")){
                passphrase.setVisible(false);
            }
            else
                passphrase.setVisible(true);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
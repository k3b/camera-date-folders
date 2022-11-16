/*
 * Copyright (C) 2022 Andreas Kromke, andreas.kromke@gmail.com
 *
 * This program is free software; you can redistribute it or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package de.kromke.andreas.cameradatefolders;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;


public class StatusAndPrefs
{
    private static final String LOG_TAG = "CDF : StatusAndPrefs";
    public static final String PREF_CAM_FOLDER_URI = "prefCamFolderUri";
    public static final String PREF_DEST_FOLDER_URI = "prefDestFolderUri";
    public static final String PREF_FOLDER_SCHEME = "prefFolderScheme";
    public static final String PREF_BACKUP_COPY = "prefBackupCopy";
    public static final String PREF_FORCE_FILE_MODE = "prefForceFileMode";
    public static final String PREF_DRY_RUN = "prefDryRun";
    public static final String PREF_SKIP_TIDY = "prefSkipTidy";
    // status
    public static boolean bSortRunning = false;
    public static boolean bRevertRunning = false;
    // prefs
    private static SharedPreferences mPrefs = null;
    public static String mCamFolder = null;
    public static String mDestFolder = null;
    public static String mFolderScheme = null;
    public static boolean mbBackupCopy = false;
    public static boolean mbFullFileAccess = false;
    public static boolean mbForceFileMode = false;
    public static boolean mbDryRun = false;
    public static boolean mbSkipTidy = false;


    /**************************************************************************
     *
     * on Activity start: get all values
     *
     *************************************************************************/
    public static void Init(Context context)
    {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        read();
    }


    /**************************************************************************
     *
     * get all values
     *
     *************************************************************************/
    private static void read()
    {
        mCamFolder = mPrefs.getString(PREF_CAM_FOLDER_URI, null);
        mDestFolder = mPrefs.getString(PREF_DEST_FOLDER_URI, null);
        mFolderScheme = mPrefs.getString(PREF_FOLDER_SCHEME, "ymd");
        mbBackupCopy = mPrefs.getBoolean(PREF_BACKUP_COPY, false);
        mbForceFileMode = mPrefs.getBoolean(PREF_FORCE_FILE_MODE, false);
        mbDryRun = mPrefs.getBoolean(PREF_DRY_RUN, false);
        mbSkipTidy = mPrefs.getBoolean(PREF_SKIP_TIDY, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            mbFullFileAccess = Environment.isExternalStorageManager();
        }
    }


    /**************************************************************************
     *
     * reset all values
     *
     *************************************************************************/
    public static void reset()
    {
        SharedPreferences.Editor prefEditor;
        if (mPrefs != null)
        {
            prefEditor = mPrefs.edit();
            prefEditor.remove(PREF_CAM_FOLDER_URI);
            prefEditor.remove(PREF_DEST_FOLDER_URI);
            prefEditor.remove(PREF_FOLDER_SCHEME);
            prefEditor.remove(PREF_BACKUP_COPY);
            prefEditor.remove(PREF_FORCE_FILE_MODE);
            prefEditor.remove(PREF_DRY_RUN);
            prefEditor.remove(PREF_SKIP_TIDY);
            prefEditor.apply();
        }
        else
        {
            Log.e(LOG_TAG, "reset() : lost Context");
        }

        read();
    }


    /**************************************************************************
     *
     * change value of any type
     *
     *************************************************************************/
    public static void writeValue(final String key, Object val)
    {
        SharedPreferences.Editor prefEditor;
        boolean isBool = false;
        boolean isString = false;
        if (mPrefs != null)
        {
            prefEditor = mPrefs.edit();
        }
        else
        {
            prefEditor = null;
            Log.e(LOG_TAG, "chgValue() : lost Context");
        }

        switch(key)
        {
            case PREF_CAM_FOLDER_URI:
                mCamFolder = (String) val;
                isString = true;
                break;

            case PREF_DEST_FOLDER_URI:
                mDestFolder = (String) val;
                isString = true;
                break;

            case PREF_FOLDER_SCHEME:
                mFolderScheme = (String) val;
                isString = true;
                break;

            case PREF_BACKUP_COPY:
                mbBackupCopy = (boolean) val;
                isBool = true;
                break;

            case PREF_FORCE_FILE_MODE:
                mbForceFileMode = (boolean) val;
                isBool = true;
                break;

            case PREF_DRY_RUN:
                mbDryRun = (boolean) val;
                isBool = true;
                break;

            case PREF_SKIP_TIDY:
                mbSkipTidy = (boolean) val;
                isBool = true;
                break;
        }

        if (prefEditor != null)
        {
            if (isString)
            {
                prefEditor.putString(key, (String) val);
            }
            else
            if (isBool)
            {
                prefEditor.putBoolean(key, (boolean) val);
            }

            prefEditor.apply();
        }
    }
}

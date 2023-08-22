/*
 * Copyright (C) 2017-20 Andreas Kromke, andreas.kromke@gmail.com
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
import android.net.Uri;
import android.util.Log;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * From https://invent.kde.org/multimedia/kid3/-/blob/master/android/Kid3Activity.java
 *
 * Nexus 5 (Android 6) passes:
 *  content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera
 *  ->
 *          final String primaryPath = "/storage/emulated/0";
 *
 * on SD card:
 *  content://com.android.externalstorage.documents/tree/9C33-6BBD%3ADCIM%2FCamera
 *  ->
 *          final String genericPath = "/storage/9C33-6BBD";
 *
 * @noinspection JavadocBlankLines, JavadocLinkAsPlainText
 */

public class UriToPath
{
    private static final String LOG_TAG = "UMPL : UTP"; //for debugging purposes.
    private static final String STORAGE_PROVIDER = "com.android.externalstorage.documents";
    private static final String PATH_TREE = "tree";
    //private static final String PATH_DOCUMENT = "document";

    @SuppressWarnings("unused")
    public static String getPathFromUri(Context context, @NonNull Uri uri)
    {
        String intentScheme = uri.getScheme();

        // content or file
        if ("file".equals(intentScheme))
        {
            Log.d(LOG_TAG, "getPathFromUri(): file scheme");
            String filePath = uri.getPath();
            if (filePath != null)
            {
                Log.d(LOG_TAG, "getPathFromUri(): got path from Uri");
                return filePath;
            }
            else
            {
                Log.w(LOG_TAG, "getPathFromUri(): no path from Uri");
                return uri.toString();
            }
        }
        else if ("content".equals(intentScheme))
        {
            Log.d(LOG_TAG, "getPathFromUri(): content scheme");

            String authority = uri.getAuthority();
            if (STORAGE_PROVIDER.equals(authority))
            {
                List<String> paths = uri.getPathSegments();
                if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0)))
                {
                    String path = paths.get(1);
                    final String[] split = path.split(":");
                    if (split.length == 2)
                    {
                        String prefix;
                        if (split[0].equals("primary"))
                        {
                            prefix = "/storage/emulated/0";
                        }
                        else
                        {
                            prefix = "/storage/" + split[0];
                        }
                        path = prefix + "/" + split[1];
                        Log.e(LOG_TAG, "getPathFromUri(): path is " + path);
                        return path;
                    }
                    else
                    {
                        Log.e(LOG_TAG, "getPathFromUri(): cannot decode tree: " + path);
                        return null;
                    }
                }
            }

        }

        return null;
    }

}

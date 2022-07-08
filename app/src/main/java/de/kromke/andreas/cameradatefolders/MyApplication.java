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

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// used for storage that survives Activity creation and deletion
public class MyApplication extends Application
{
    private static final String LOG_TAG = "CDF : App";
//    ExecutorService executorService = Executors.newFixedThreadPool(4);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    //Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());

    private WorkerThread thread = null;
    private MainActivity mActivityForThread = null;  // null: thread is not running

    // called from UI thread
    int runWorkerThread(MainActivity activity, Uri uri, String scheme, boolean bDryRun, boolean bForceFileMode)
    {
        if ((thread != null) && (thread.isBusy))
        {
            Log.e(LOG_TAG, "runWorkerThread() -- busy");
            return -1;
        }

        if (thread == null)
        {
            /*
            thread = new ThreadTest();
            thread.handler = mainThreadHandler; // ???
            */
            thread = new WorkerThread(this);
        }
        thread.setParameters(activity, uri, scheme, bDryRun, bForceFileMode);
        mActivityForThread = activity;
        executor.execute(thread);
        return 0;
    }

    // called from UI thread
    void stopWorkerThread()
    {
        if (thread != null)
        {
            thread.stop();
        }
    }

        // called from worker thread
    void msgFromWorkerThread(int result1, int result2, int result3, final String text, boolean threadEnded)
    {
        if (mActivityForThread != null)
        {
            mActivityForThread.messageFromThread(result1, result2, result3, text, threadEnded);
        }
        if (threadEnded)
        {
            Log.d(LOG_TAG, "msgFromWorkerThread() -- thread ended");
            mActivityForThread = null;
        }
    }
}

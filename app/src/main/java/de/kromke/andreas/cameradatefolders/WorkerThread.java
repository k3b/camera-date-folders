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
import android.net.Uri;
import android.os.Build;
import android.util.Log;

class WorkerThread implements Runnable, Utils.ProgressCallBack
{
    private static final String LOG_TAG = "CDF : WT";
    public boolean isBusy = false;
    private boolean mustStop = false;
    private final MyApplication mApplication;
    // parameters
    private Context mContext = null;
    private Uri mTreeUri = null;
    boolean mbSortYear = true;
    boolean mbSortMonth = true;
    boolean mbSortDay = true;
    private boolean mbDryRun = false;
    private boolean mbForceFileMode = false;
    private Utils mUtils = null;
    // result
    private int nSuccess = 0;
    private int nFailure = 0;
    private int nUnchanged = 0;

    public WorkerThread(MyApplication application)
    {
        mApplication = application;
    }

    public void setParameters(Context context, Uri uri, String scheme, boolean bDryRun, boolean bForceFileMode)
    {
        mTreeUri = uri;
        switch (scheme)
        {
            default:
            case "ymd":
                mbSortYear = true;
                mbSortMonth = true;
                mbSortDay = true;
                break;
            case "md":
                mbSortYear = false;
                mbSortMonth = true;
                mbSortDay = true;
                break;
            case "yd":
                mbSortYear = true;
                mbSortMonth = false;
                mbSortDay = true;
                break;
            case "ym":
                mbSortYear = true;
                mbSortMonth = true;
                mbSortDay = false;
                break;
            case "d":
                mbSortYear = false;
                mbSortMonth = false;
                mbSortDay = true;
                break;
            case "m":
                mbSortYear = false;
                mbSortMonth = true;
                mbSortDay = false;
                break;
            case "y":
                mbSortYear = true;
                mbSortMonth = false;
                mbSortDay = false;
                break;
            case "flat":
                mbSortYear = false;
                mbSortMonth = false;
                mbSortDay = false;
                break;
        }
        mbDryRun = bDryRun;
        mbForceFileMode = bForceFileMode;
        mContext = context;
    }

    private void done()
    {
        if (mApplication != null)
        {
            mApplication.msgFromWorkerThread(nSuccess, nFailure, nUnchanged, null, true);
        }
        isBusy = false;
    }

    // also used as callback from Utils
    public void tellProgress(final String text)
    {
        if (mApplication != null)
        {
            mApplication.msgFromWorkerThread(nSuccess, nFailure, nUnchanged, text, false);
        }
    }

    // main thread function, runs in thread context
    public void run()
    {
        isBusy = true;
        mustStop = false;
        Log.d(LOG_TAG, "run()");

        boolean bFileMode = mbForceFileMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.N);

        nSuccess = 0;
        nFailure = 0;
        nUnchanged = 0;
        if (mTreeUri != null)
        {
            mUtils = new Utils(mContext, mTreeUri, mbSortYear, mbSortMonth, mbSortDay, bFileMode);
            int ret = mUtils.gatherFiles(this);
            if (mustStop && (ret == 0))
            {
                tellProgress("stopped on demand");
            }

            /*
            /// +test code
            for (int i = 0; i < 100; i++)
            {
                tellProgress("" + i + " lines");
            }
            /// -test code
            */
            nUnchanged = mUtils.mUnchangedFiles;
            if (ret > 0)
            {
                tellProgress("" + ret + " files collected\n");
                int i = 0;
                for (Utils.mvOp op: mUtils.getOps())
                {
                    if (mustStop)
                    {
                        tellProgress("stopped on demand");
                        break;
                    }

                    String fileName = op.getName();
                    Log.d(LOG_TAG, " mv " + op.getSrcPath() + fileName + " ==> " + op.getDstPath());
                    if (mbDryRun)
                    {
                        nSuccess++;
                    }
                    else
                    {
                        boolean retm = op.move();
                        if (retm)
                        {
                            nSuccess++;
                        } else
                        {
                            nFailure++;
                        }
                    }
                    i++;
                    tellProgress(fileName + " (" + i + "/" + mUtils.mOps.size() + ")");
                }
                Log.d(LOG_TAG, "files moved: " + nSuccess + ", failures: " + nFailure);
            }
        }

        mUtils = null;
        done();
    }

    public void stop()
    {
        if (isBusy)
        {
            mustStop = true;
            if (mUtils != null)
            {
                mUtils.mustStop = true;
            }
        }
    }
}

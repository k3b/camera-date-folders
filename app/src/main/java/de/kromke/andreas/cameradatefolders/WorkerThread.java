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

class WorkerThread implements Runnable
{
    private static final String LOG_TAG = "CDF : WT";
    private final MyApplication mApplication;
    // parameters
    private Context mContext = null;
    private Uri mTreeUri = null;
    // result
    private int nSuccess = 0;
    private int nFailure = 0;
    private int nUnchanged = 0;

    public WorkerThread(MyApplication application)
    {
        mApplication = application;
    }

    public void setParameters(Context context, Uri uri)
    {
        mTreeUri = uri;
        mContext = context;
    }

    private void done()
    {
        if (mApplication != null)
        {
            mApplication.msgFromWorkerThread(nSuccess, nFailure, nUnchanged, null, true);
        }
    }

    private void tellProgress(final String text)
    {
        if (mApplication != null)
        {
            mApplication.msgFromWorkerThread(nSuccess, nFailure, nUnchanged, text, false);
        }
    }

    public void run()
    {
        Log.d(LOG_TAG, "run()");
        nSuccess = 0;
        nFailure = 0;
        nUnchanged = 0;
        if (mTreeUri != null)
        {
            Utils utils = new Utils(mContext, mTreeUri,true, true, true);
            int ret = utils.gatherFiles();
            nUnchanged = utils.mUnchangedFiles;
            if (ret > 0)
            {
                tellProgress("" + ret + " files collected");
                for (Utils.mvOp op: utils.mOps)
                {
                    Log.d(LOG_TAG, " mv " + op.srcPath + op.srcFile.getName() + " ==> " + op.dstPath);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    {
                        boolean retm = utils.mvFile(op);
                        if (retm)
                        {
                            nSuccess++;
                        }
                        else
                        {
                            nFailure++;
                        }
                    }
                    else
                    {
                        nFailure++;
                        Log.d(LOG_TAG, " not supported, needs API level 24");
                    }
                    tellProgress(op.srcFile.getName());
                }
                Log.d(LOG_TAG, "files moved: " + nSuccess + ", failures: " + nFailure);
            }
        }

        done();
    }
}

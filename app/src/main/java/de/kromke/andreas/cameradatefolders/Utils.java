package de.kromke.andreas.cameradatefolders;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

import androidx.documentfile.provider.DocumentFile;

public class Utils
{
    private static final String LOG_TAG = "CDF : Utils";
    Context mContext;
    int directoryLevel;
    ArrayList<mvOp> mOps = null;

    public static class camFileDate
    {
        public int year;        // 1999 .. 2999
        public int month;       // 1 .. 12
        public int day;         // 1 .. 31
    }

    public static class mvOp
    {
        public String srcPath;
        public String dstPath;
    }

    Utils(Context context)
    {
        mContext = context;
    }

    public int gatherFiles(Uri treeUri)
    {
        mOps = new ArrayList<>();
        directoryLevel = 8;             // limit
        DocumentFile df = DocumentFile.fromTreeUri(mContext, treeUri);
        if (df != null)
        {
            gatherDirectory(df);
        }
        return mOps.size();
    }

    private int getNumber(final String str)
    {
        try
        {
            return Integer.parseInt(str);
        }
        catch(Exception e)
        {
            return -1;
        }
    }

    private boolean isCameraFileType(final String name)
    {
        //
        // check file name extension
        //

        return (name.endsWith(".mp4") ||
                name.endsWith(".3gp") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg"));
    }

    private camFileDate isCameraFile(final String name)
    {
        //
        // skip prefix containing of non-digit characters
        //

        int i;
        for (i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (Character.isDigit(c))
            {
                break;
            }
        }

        //
        // prefix must be followed by 8 digits
        //

        if (i >= name.length() - 8)
        {
            // no decimal digit found
            return null;
        }

        for (int j = 1; j < 8; j++)
        {
            char c = name.charAt(i + j);
            if (!Character.isDigit(c))
            {
                return null;
            }
        }

        //
        // 8 digits must be followed by non-digit
        //

        char c = name.charAt(i + 8);
        if (Character.isDigit(c))
        {
            return null;
        }

        //
        // get year
        //

        int year = getNumber(name.substring(i, i + 4));
        if ((year < 1999) || (year > 2999))
        {
            return null;
        }

        //
        // get month
        //

        int month = getNumber(name.substring(i + 4, i + 6));
        if ((month < 1) || (month > 12))
        {
            return null;
        }

        //
        // get year
        //

        int day = getNumber(name.substring(i + 6, i + 8));
        if ((day < 1) || (day > 31))
        {
            return null;
        }

        camFileDate ret = new camFileDate();
        ret.year = year;
        ret.month = month;
        ret.day = day;
        return ret;
    }

    private void gatherDirectory(DocumentFile dd)
    {
        directoryLevel--;
        if (directoryLevel < 0)
        {
            Log.w(LOG_TAG, "gatherDirectory() -- path depth overflow");
            return;
        }

        Log.d(LOG_TAG, "gatherDirectory() -- ENTER DIRECTORY " + dd.getName());

        DocumentFile[] entries = dd.listFiles();
        Log.d(LOG_TAG, "gatherDirectory() -- number of files found: " + entries.length);
        for (DocumentFile df: entries)
        {
            final String name = df.getName();
            if (name == null)
            {
                Log.w(LOG_TAG, "gatherDirectory() -- skip null name");
            }
            else
            if (name.startsWith("."))
            {
                Log.w(LOG_TAG, "gatherDirectory() -- skip dot files: " + name);
            }
            else
            if (df.isDirectory())
            {
                gatherDirectory(df);
            }
            else
            {
                if (isCameraFileType(name))
                {
                    camFileDate date = isCameraFile(name);
                    if (date != null)
                    {
                        Log.d(LOG_TAG, "gatherDirectory() -- camera file found: " + name);
                    }
                    else
                    {
                        Log.w(LOG_TAG, "gatherDirectory() -- image file does not look like camera file: " + name);
                    }
                }
                else
                {
                    Log.w(LOG_TAG, "gatherDirectory() -- non matching file type: " + name);
                }
            }
        }

        Log.d(LOG_TAG, "gatherDirectory() -- LEAVE DIRECTORY " + dd.getName());
    }
}

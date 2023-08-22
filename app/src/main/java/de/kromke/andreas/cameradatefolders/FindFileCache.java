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

import java.util.LinkedList;

import androidx.documentfile.provider.DocumentFile;

/** @noinspection JavadocBlankLines*/ // the actual work is done here
public class FindFileCache
{
    private static final int cacheMaxLen = 8;
    LinkedList<findFileResult> mList;

    // entry for findFile() cache
    private static class findFileResult
    {
        public DocumentFile parentDir;
        public String childName;
        public DocumentFile child;
    }


    /**************************************************************************
     *
     * constructor
     *
     *************************************************************************/
    FindFileCache()
    {
        mList = new LinkedList<>();
    }


    /**************************************************************************
     *
     * search list
     *
     *************************************************************************/
    public DocumentFile findFileCached(DocumentFile parentDir, final String childName)
    {
        int cacheLen = mList.size();
        for (int i = 0; i < cacheLen; i++)
        {
            findFileResult entry = mList.get(i);
            if ((entry.parentDir == parentDir) && (entry.childName.equals(childName)))
            {
                // cache hit. Put to start of queue.
                if (i != 0)
                {
                    mList.remove(i);
                    mList.push(entry);
                }
                return entry.child;
            }
        }

        // not in cache found. Call system.
        DocumentFile child = parentDir.findFile(childName);
        if (child != null)
        {
            // create new cache entry
            findFileResult entry = new findFileResult();
            entry.parentDir = parentDir;
            entry.childName = childName;
            entry.child = child;
            // remove oldest entry
            if (cacheLen >= cacheMaxLen)
            {
                mList.removeLast();
            }
            // put new entry to start of queue
            mList.push(entry);
        }

        return child;
    }
}

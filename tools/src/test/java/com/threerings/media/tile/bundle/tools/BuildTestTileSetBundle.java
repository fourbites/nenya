//
// Nenya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// https://github.com/threerings/nenya
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.media.tile.bundle.tools;

import java.net.URL;
import java.util.HashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.samskivert.io.PersistenceException;

import com.threerings.media.tile.TileSetIDBroker;

public class BuildTestTileSetBundle
{
    public static void main (String[] args)
    {
        try {
            TileSetIDBroker broker = new DummyTileSetIDBroker();

            // sort out some paths
            String configPath = getResourcePath(CONFIG_PATH);
            String descPath = getResourcePath(BUNDLE_DESC_PATH);
            String targetPath = getResourcePath(TARGET_PATH);

            // create our bundler and get going
            TileSetBundler bundler = new TileSetBundler(configPath);
            File descFile = new File(descPath);
            bundler.createBundle(broker, descFile, targetPath);

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    protected static String getResourcePath (String path) throws IOException
    {
        URL url = BuildTestTileSetBundle.class.getClassLoader().getResource(path);
        if (url == null) throw new FileNotFoundException(path);
        if (!"file".equals(url.getProtocol())) throw new FileNotFoundException(
            "Test resource not available via file URL: " + url);
        return url.getPath();
    }

    /** Dummy tileset id broker that makes up tileset ids (which are consistent in the course of
     * execution of the application, but not between invocations). */
    protected static class DummyTileSetIDBroker extends HashMap<String,Integer>
        implements TileSetIDBroker
    {
        public int getTileSetID (String tileSetName)
            throws PersistenceException
        {
            Integer id = get(tileSetName);
            if (id == null) {
                id = new Integer(++_nextId);
                put(tileSetName, id);
            }
            return id.intValue();
        }

        public boolean tileSetMapped (String tileSetName)
        {
            return containsKey(tileSetName);
        }

        public void commit ()
            throws PersistenceException
        {
        }

        protected int _nextId;
    }

    protected static final String CONFIG_PATH = "rsrc/media/tile/bundle/tools/bundler-config.xml";
    protected static final String BUNDLE_DESC_PATH = "rsrc/media/tile/bundle/tools/bundle.xml";
    protected static final String TARGET_PATH = "rsrc/media/tile/bundle/tools/bundle.jar";
}

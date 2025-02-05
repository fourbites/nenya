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

import java.util.ArrayList;
import java.util.Iterator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;

import com.samskivert.io.PersistenceException;
import com.samskivert.io.StreamUtil;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.IntMap;

import com.threerings.resource.FastImageIO;

import com.threerings.tools.JSONConversion;

import com.threerings.media.tile.ImageProvider;
import com.threerings.media.tile.ObjectTileSet;
import com.threerings.media.tile.SimpleCachingImageProvider;
import com.threerings.media.tile.TileSet;
import com.threerings.media.tile.TileSetIDBroker;
import com.threerings.media.tile.TrimmedObjectTileSet;
import com.threerings.media.tile.bundle.BundleUtil;
import com.threerings.media.tile.bundle.TileSetBundle;
import com.threerings.media.tile.tools.xml.TileSetRuleSet;
import com.threerings.media.tile.util.TileSetTrimmer;

import static com.threerings.media.Log.log;

/**
 * The tileset bundler is used to create tileset bundles from a set of XML
 * tileset descriptions in a bundle description file. The bundles contain
 * a serialized representation of the tileset objects along with the
 * actual image files referenced by those tilesets.
 *
 * <p> The organization of the bundle description file is customizable
 * based on the an XML configuration file provided to the tileset bundler
 * when constructed.  The bundler configuration maps XML paths to tileset
 * parsers. An example configuration follows:
 *
 * <pre>
 * &lt;bundler-config&gt;
 *   &lt;mapping&gt;
 *     &lt;path&gt;bundle.tilesets.uniform&lt;/path&gt;
 *     &lt;ruleset&gt;
 *       com.threerings.media.tile.tools.xml.UniformTileSetRuleSet
 *     &lt;/ruleset&gt;
 *   &lt;/mapping&gt;
 *   &lt;mapping&gt;
 *     &lt;path&gt;bundle.tilesets.object&lt;/path&gt;
 *     &lt;ruleset&gt;
 *       com.threerings.media.tile.tools.xml.ObjectTileSetRuleSet
 *     &lt;/ruleset&gt;
 *   &lt;/mapping&gt;
 * &lt;/bundler-config&gt;
 * </pre>
 *
 * This configuration would be used to parse a bundle description that
 * looked something like the following:
 *
 * <pre>
 * &lt;bundle&gt;
 *   &lt;tilesets&gt;
 *     &lt;uniform&gt;
 *       &lt;tileset&gt;
 *         &lt;!-- ... --&gt;
 *       &lt;/tileset&gt;
 *     &lt;/uniform&gt;
 *     &lt;object&gt;
 *       &lt;tileset&gt;
 *         &lt;!-- ... --&gt;
 *       &lt;/tileset&gt;
 *     &lt;/object&gt;
 *   &lt;/tilesets&gt;
 * </pre>
 *
 * The class specified in the <code>ruleset</code> element must derive
 * from {@link TileSetRuleSet}. The images that will be included in the
 * bundle must be in the same directory as the bundle description file and
 * the tileset descriptions must reference the images without a preceding
 * path.
 */
public class TileSetBundler
{
    /**
     * Wraps up the configuration options for writing a tile set bundle to disk and provides a
     * method to perform the write.
     */
    public static class Writer
    {
        /** The target jar file or directory. */
        public final BundleWriter bwriter;

        /** The bundle to write. */
        public final TileSetBundle bundle;

        /** Loads images for the bundle. */
        public final ImageProvider improv;

        /** Modification time of the newest source file. */
        public final long newestSource;

        /**
         * Creates a new writer.
         *
         * @param bwriter the tileset bundle file or directory that will be created.
         * @param bundle contains the tilesets we'd like to save out to the bundle.
         * @param improv the image provider.
         */
        public Writer (BundleWriter bwriter, TileSetBundle bundle, ImageProvider improv,
            long newestSource)
        {
            this.bundle = bundle;
            this.bwriter = bwriter;
            this.improv = improv;
            this.newestSource = newestSource;
        }

        /**
         * Sets whether or not we trim the tileset's image in the process of writing out the
         * new bundle. Trimming will mutate tilesets directly, so beware.
         */
        public Writer trimImages (boolean trim)
        {
            this.trim = trim;
            return this;
        }

        /**
         * Sets the Packer to use when trimming.
         */
        public Writer usePacker (Supplier<TileSetTrimmer.Packer> packer)
        {
            this.packer = packer;
            return trimImages(packer != null);
        }

        /**
         * Sets whether or not we write out raw images.
         */
        public Writer useRawImages (boolean raw)
        {
            this.raw = raw;
            return this;
        }

        /**
         * Sets the image base.
         * TODO: learn what this does and improve comment. should it just be a special improv?
         */
        public Writer imageBase (String imageBase)
        {
            this.imageBase = imageBase;
            return this;
        }

        /**
         * Sets whether we write out a json file containing the meta data.
         * @param json if non-null, used to write out the bundle contents to tsbundles.json text
         *   file. Otherwise, the default binary obbject output is used.
         */
        public Writer useJson (JSONConversion.Config json)
        {
            this.json = json;
            return this;
        }

        /**
         * Using the configured options, creates the new target bundle.
         */
        public void create ()
            throws IOException
        {
            TileSetBundler.createBundle(this);
        }

        boolean trim = true;
        boolean raw = true;
        Supplier<TileSetTrimmer.Packer> packer = new Supplier<TileSetTrimmer.Packer>() {
            public TileSetTrimmer.Packer get () {
                return new TileSetTrimmer.StripPacker();
            }
        };
        String imageBase;
        JSONConversion.Config json;
    }

    /**
     * Constructs a tileset bundler with the specified path to a bundler
     * configuration file. The configuration file will be loaded and used
     * to configure this tileset bundler.
     */
    public TileSetBundler (String configPath)
        throws IOException
    {
        this(new File(configPath));
    }

    /**
     * Constructs a tileset bundler with the specified bundler config
     * file.
     */
    public TileSetBundler (File configFile)
        throws IOException
    {
        // we parse our configuration with a digester
        Digester digester = new Digester();

        // push our mappings array onto the stack
        ArrayList<Mapping> mappings = Lists.newArrayList();
        digester.push(mappings);

        // create a mapping object for each mapping entry and append it to
        // our mapping list
        digester.addObjectCreate("bundler-config/mapping", Mapping.class.getName());
        digester.addSetNext("bundler-config/mapping", "add", "java.lang.Object");

        // configure each mapping object with the path and ruleset
        digester.addCallMethod("bundler-config/mapping", "init", 2);
        digester.addCallParam("bundler-config/mapping/path", 0);
        digester.addCallParam("bundler-config/mapping/ruleset", 1);

        // now go like the wind
        FileInputStream fin = new FileInputStream(configFile);
        try {
            digester.parse(fin);
        } catch (SAXException saxe) {
            String errmsg = "Failure parsing bundler config file " +
                "[file=" + configFile.getPath() + "]";
            throw (IOException) new IOException(errmsg).initCause(saxe);
        }
        fin.close();

        // create our digester
        _digester = new Digester();

        // use the mappings we parsed to configure our actual digester
        int msize = mappings.size();
        for (int ii = 0; ii < msize; ii++) {
            Mapping map = mappings.get(ii);
            try {
                TileSetRuleSet ruleset = (TileSetRuleSet)Class.forName(map.ruleset).newInstance();

                // configure the ruleset
                ruleset.setPrefix(map.path);
                // add it to the digester
                _digester.addRuleSet(ruleset);
                // and add a rule to stick the parsed tilesets onto the
                // end of an array list that we'll put on the stack
                _digester.addSetNext(ruleset.getPath(), "add", "java.lang.Object");

            } catch (Exception e) {
                String errmsg = "Unable to create tileset rule set " +
                    "instance [mapping=" + map + "].";
                throw (IOException) new IOException(errmsg).initCause(e);
            }
        }
    }

    /**
     * Prepares to create a tileset bundle at the location specified by the
     * <code>target</code> parameter, based on the description
     * provided via the <code>bundleDesc</code> parameter.
     *
     * @param idBroker the tileset id broker that will be used to map
     * tileset names to tileset ids.
     * @param bundleDesc a file object pointing to the bundle description
     * file.
     * @param target the tileset bundle file or directory that will be created.
     *
     * @return a writer object that can be used to configure and create the target
     * bundle, or null if the target is up to date with respect to all source files
     *
     * @exception IOException thrown if an error occurs reading, writing
     * or processing anything.
     */
    public Writer process (
        TileSetIDBroker idBroker, final File bundleDesc, BundleWriter target)
        throws IOException
    {
        // stick an array list on the top of the stack into which we will
        // collect parsed tilesets
        ArrayList<TileSet> sets = Lists.newArrayList();
        _digester.push(sets);

        // parse the tilesets
        FileInputStream fin = new FileInputStream(bundleDesc);
        try {
            _digester.parse(fin);
        } catch (SAXException saxe) {
            String errmsg = "Failure parsing bundle description file " +
                "[path=" + bundleDesc.getPath() + "]";
            throw (IOException) new IOException(errmsg).initCause(saxe);
        } finally {
            fin.close();
        }

        // we want to make sure that at least one of the tileset image
        // files or the bundle definition file is newer than the bundle
        // file, otherwise consider the bundle up to date
        long newest = bundleDesc.lastModified();

        // create a tileset bundle to hold our tilesets
        TileSetBundle bundle = new TileSetBundle();

        // add all of the parsed tilesets to the tileset bundle
        try {
            for (int ii = 0; ii < sets.size(); ii++) {
                TileSet set = sets.get(ii);
                String name = set.getName();

                // let's be robust
                if (name == null) {
                    log.warning("Tileset was parsed, but received no name " +
                                "[set=" + set + "]. Skipping.");
                    continue;
                }

                // make sure this tileset's image file exists and check its last modified date
                File tsfile = new File(bundleDesc.getParent(),
                                       set.getImagePath());
                if (!tsfile.exists()) {
                    System.err.println("Tile set missing image file " +
                                       "[bundle=" + bundleDesc.getPath() +
                                       ", name=" + set.getName() +
                                       ", imgpath=" + tsfile.getPath() + "].");
                    continue;
                }
                if (tsfile.lastModified() > newest) {
                    newest = tsfile.lastModified();
                }

                // assign a tilset id to the tileset and bundle it
                try {
                    int tileSetId = idBroker.getTileSetID(name);
                    bundle.addTileSet(tileSetId, set);
                } catch (PersistenceException pe) {
                    String errmsg = "Failure obtaining a tileset id for " +
                        "tileset [set=" + set + "].";
                    throw (IOException) new IOException(errmsg).initCause(pe);
                }
            }

            // clear out our array list in preparation for another go
            sets.clear();

        } finally {
            // before we go, we have to commit our brokered tileset ids
            // back to the broker's persistent store
            try {
                idBroker.commit();
            } catch (PersistenceException pe) {
                log.warning("Failure committing brokered tileset ids " +
                            "back to broker's persistent store " +
                            "[error=" + pe + "].");
            }
        }

        // see if our newest file is newer than the tileset bundle
        if (target.isNewerThan(newest)) {
            return null;
        }

        // create an image provider for loading our tileset images
        SimpleCachingImageProvider improv = new SimpleCachingImageProvider() {
            @Override
            protected BufferedImage loadImage (String path)
                throws IOException {
                return ImageIO.read(new File(bundleDesc.getParent(), path));
            }
        };

        return new Writer(target, bundle, improv, newest).imageBase(bundleDesc.getParent());
    }

    /**
     * Create the tileset bundle on disk using previously configured options.
     */
    protected static void createBundle (Writer target)
        throws IOException
    {
        try {
            // write all of the image files to the bundle, converting the
            // tilesets to trimmed tilesets in the process
            Iterator<Integer> iditer = target.bundle.enumerateTileSetIds();

            // Store off the updated TileSets in a separate Map so we can wait to change the
            // bundle till we're done iterating.
            HashIntMap<TileSet> toUpdate = new HashIntMap<TileSet>();
            while (iditer.hasNext()) {
                processBundleImage(iditer.next().intValue(), target, toUpdate);
            }
            target.bundle.putAll(toUpdate);

            writeUpdatedBundle(target);

            // finally close up the jar file and call ourself done
            target.bwriter.close();

        } catch (Exception e) {
            // remove the incomplete jar file and rethrow the exception
            target.bwriter.close();
            if (!target.bwriter.delete()) {
                log.warning("Failed to close botched bundle '" + target + "'.");
            }
            String errmsg = "Failed to create bundle " + target + ": " + e;
            throw (IOException) new IOException(errmsg).initCause(e);
        }
    }

    protected static void processBundleImage (
        int tileSetId, Writer target, IntMap<TileSet> toUpdate) throws IOException
    {
        TileSet set = target.bundle.getTileSet(tileSetId);
        String imagePath = set.getImagePath();

        // sanity checks
        if (imagePath == null) {
            log.warning("Tileset contains no image path " +
                        "[set=" + set + "]. It ain't gonna work.");
            return;
        }

        File ifile = new File(target.imageBase, imagePath);
        long sourceTime = Math.max(target.newestSource, ifile.lastModified());

        // if this is an object tileset, trim it
        if (target.trim && (set instanceof ObjectTileSet)) {
            // add .raw if requested
            if (target.raw) {
                imagePath = adjustImagePath(imagePath);
            }

            if (target.bwriter.isPathNewerThan(imagePath, sourceTime)) {
                return;
            }

            OutputStream dest = target.bwriter.startNewFile(imagePath);

            // set the tileset up with an image provider; we
            // need to do this so that we can trim it!
            set.setImageProvider(target.improv);

            try {
                // create a trimmed object tileset, which will
                // write the trimmed tileset image to the target file
                TrimmedObjectTileSet tset =
                    TrimmedObjectTileSet.trimObjectTileSet((ObjectTileSet)set, dest,
                        target.raw ? FastImageIO.FILE_SUFFIX : "png", target.packer.get());
                tset.setImagePath(imagePath);
                // replace the original set with the trimmed
                // tileset in the tileset bundle
                toUpdate.put(tileSetId, tset);

            } catch (Exception e) {
                e.printStackTrace(System.err);

                String msg = "Error adding tileset to bundle " + imagePath +
                             ", " + set.getName() + ": " + e;
                throw (IOException) new IOException(msg).initCause(e);
            }

        } else {
            // read the image file and convert it to our custom
            // format in the bundle
            try {
                BufferedImage image = ImageIO.read(ifile);
                if (target.raw && FastImageIO.canWrite(image)) {
                    imagePath = adjustImagePath(imagePath);
                    if (!target.bwriter.isPathNewerThan(imagePath, sourceTime)) {
                        OutputStream dest = target.bwriter.startNewFile(imagePath);
                        set.setImagePath(imagePath);
                        FastImageIO.write(image, dest);
                    }
                } else {
                    if (!target.bwriter.isPathNewerThan(imagePath, sourceTime)) {
                        OutputStream dest = target.bwriter.startNewFile(imagePath);
                        FileInputStream imgin = new FileInputStream(ifile);
                        StreamUtil.copy(imgin, dest);
                    }
                }
            } catch (Exception e) {
                String msg = "Failure bundling image " + ifile +
                    ": " + e;
                throw (IOException) new IOException(msg).initCause(e);
            }
        }
    }

    protected static void writeUpdatedBundle (Writer target)
        throws IOException
    {
        String meta = target.json != null ?
            BundleUtil.METADATA_JSON_PATH : BundleUtil.METADATA_PATH;

        if (target.bwriter.isPathNewerThan(meta, target.newestSource)) {
            return;
        }

        // now write a serialized representation of the tileset bundle
        if (target.json != null) {
            JSONArray array = new JSONArray();
            for (Iterator<Integer> tileSetId = target.bundle.enumerateTileSetIds();
                    tileSetId.hasNext(); ) {
                JSONObject tset = new JSONObject();
                int id = tileSetId.next();
                tset.put("id", id);
                tset.put("set", target.json.convert(target.bundle.get(id)));
                array.add(tset);
            }

            OutputStream fout = target.bwriter.startNewFile(meta);
            fout.write(array.toString().getBytes());
            fout.close();

        } else {
            ObjectOutputStream oout = new ObjectOutputStream(
                target.bwriter.startNewFile(BundleUtil.METADATA_PATH));
            oout.writeObject(target.bundle);
            oout.flush();
        }

    }

    /** Replaces the image suffix with <code>.raw</code>. */
    protected static String adjustImagePath (String imagePath)
    {
        int didx = imagePath.lastIndexOf(".");
        return ((didx == -1) ? imagePath :
                imagePath.substring(0, didx)) + ".raw";

    }

    /** Used to parse our configuration. */
    public static class Mapping
    {
        public String path;
        public String ruleset;

        public void init (String path, String ruleset)
        {
            this.path = path;
            this.ruleset = ruleset;
        }

        @Override
        public String toString ()
        {
            return "[path=" + path + ", ruleset=" + ruleset + "]";
        }
    }

    /** The digester we use to parse bundle descriptions. */
    protected Digester _digester;
}

package org.jboss.forge.arquillian.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.forge.furnace.util.Streams;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filter;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

public final class ShrinkWrapUtil
{
   private ShrinkWrapUtil()
   {
   }

   /**
    * Export an {@link Archive} to a {@link File}
    * 
    * @param archive Archive to export
    */
   public static void toFile(File target, final Archive<?> archive)
   {
      try
      {
         archive.as(ZipExporter.class).exportTo(target, true);
      }
      catch (Exception e)
      {
         throw new RuntimeException("Could not export deployment to file [" + target.getAbsolutePath() + "]", e);
      }
   }

   public static void unzip(File baseDir, Archive<?> archive)
   {
      try
      {
         Map<ArchivePath, Node> content = archive.getContent(new Filter<ArchivePath>()
         {
            @Override
            public boolean include(ArchivePath object)
            {
               return object.get().endsWith(".jar");
            }
         });

         for (Entry<ArchivePath, Node> entry : content.entrySet())
         {
            ArchivePath path = entry.getKey();
            File target = new File(baseDir.getAbsolutePath() + "/" + path.get().replaceFirst("/lib/", ""));
            target.mkdirs();
            target.delete();
            target.createNewFile();

            Node node = entry.getValue();
            Asset asset = node.getAsset();
            try (FileOutputStream fos = new FileOutputStream(target); InputStream is = asset.openStream();)
            {
               Streams.write(is, fos);
            }
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   /**
    * Creates a tmp folder and exports the file. Returns the URL for that file location.
    * 
    * @param archive Archive to export
    * @return
    */
   public static URL toURL(final Archive<?> archive)
   {
      // create a random named temp file, then delete and use it as a directory
      try
      {
         File root = File.createTempFile("arquillian", archive.getName());
         root.delete();
         root.mkdirs();

         File deployment = new File(root, archive.getName());
         deployment.deleteOnExit();
         archive.as(ZipExporter.class).exportTo(deployment, true);
         return deployment.toURI().toURL();
      }
      catch (Exception e)
      {
         throw new RuntimeException("Could not export deployment to temp", e);
      }
   }

   public static URL toURL(final Descriptor descriptor)
   {
      try
      {
         File deployment = createDeployment(descriptor);
         return deployment.toURI().toURL();
      }
      catch (Exception e)
      {
         throw new RuntimeException("Could not export deployment to temp", e);
      }
   }

   private static File createDeployment(final Descriptor descriptor) throws IOException
   {
      File root = createTempFile(descriptor);
      try (FileOutputStream stream = new FileOutputStream(root))
      {
         File deployment = new File(root, descriptor.getDescriptorName());
         deployment.deleteOnExit();
         descriptor.exportTo(stream);
         return deployment;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Could not export deployment to temp", e);
      }
   }

   private static File createTempFile(final Descriptor descriptor) throws IOException
   {
      // create a random named temp file, then delete and use it as a directory
      File root = File.createTempFile("arquillian", descriptor.getDescriptorName());
      root.delete();
      root.mkdirs();
      return root;
   }
}
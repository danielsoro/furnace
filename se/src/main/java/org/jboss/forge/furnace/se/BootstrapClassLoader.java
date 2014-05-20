package org.jboss.forge.furnace.se;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.jboss.forge.furnace.util.OperatingSystemUtils;

public class BootstrapClassLoader extends URLClassLoader
{
   private static final Logger log = Logger.getLogger(BootstrapClassLoader.class.getName());

   public BootstrapClassLoader(String bootstrapPath)
   {
      super(new JarLocator(bootstrapPath).find(), null);
   }

   private static class JarLocator
   {
      private String path;

      public JarLocator(String path)
      {
         this.path = path;
      }

      private URL[] find()
      {
         List<URL> result = new ArrayList<URL>();
         try
         {
            for (URL url : Collections.list(JarLocator.class.getClassLoader().getResources(path)))
            {
               String urlPath = url.getFile();
               urlPath = URLDecoder.decode(urlPath, "UTF-8");
               if (urlPath.startsWith("file:"))
               {
                  urlPath = urlPath.substring(5);
               }
               if (urlPath.indexOf('!') > 0)
               {
                  urlPath = urlPath.substring(0, urlPath.indexOf('!'));
               }
               result.add(new URL(url, "."));
               result.addAll(handle(urlPath, url));
            }
         }
         catch (Exception e)
         {
            throw new RuntimeException("Could not load jars from " + path, e);
         }

         return result.toArray(new URL[0]);
      }

      private List<URL> handle(String urlPath, URL original) throws IOException
      {
         List<URL> result = new ArrayList<URL>();
         File file = new File(urlPath);
         if (file.isDirectory())
            result = handle(file);
         else if (file.isFile())
            result = handleZipFile(file);
         else
            result = handleZipStream(original);
         return result;
      }

      private List<URL> handleZipStream(URL original) throws IOException, FileNotFoundException
      {
         List<URL> result = new ArrayList<URL>();
         InputStream stream = original.openStream();
         if (stream instanceof ZipInputStream)
         {
            File tempDir = OperatingSystemUtils.createTempDir();
            ZipEntry entry;
            while ((entry = ((ZipInputStream) stream).getNextEntry()) != null)
            {
               if (entry.getName().matches(".*\\.jar$"))
               {
                  log.log(Level.FINE, String.format("ZipEntry detected: %s len %d added %TD",
                           original.toExternalForm() + entry.getName(), entry.getSize(),
                           new Date(entry.getTime())));
                  try (FileOutputStream output = new FileOutputStream(new File(tempDir, entry.getName()));)
                  {
                     byte[] buffer = new byte[2048];

                     int len = 0;
                     while ((len = stream.read(buffer)) > 0)
                     {
                        output.write(buffer, 0, len);
                     }
                  }
               }
            }
            result = handle(tempDir);
         }
         return result;
      }

      private List<URL> handle(File file)
      {
         List<URL> result = new ArrayList<URL>();
         for (File child : file.listFiles())
         {
            if (!child.isDirectory() && child.getName().endsWith(".jar"))
            {
               try
               {
                  log.log(Level.FINE, "File entry detected: " + child.getAbsolutePath());
                  result.add(child.toURI().toURL());
               }
               catch (MalformedURLException e)
               {
                  throw new RuntimeException("Could not convert to URL " + child, e);
               }
            }
         }
         return result;
      }

      @SuppressWarnings("deprecation")
      private List<URL> handleZipFile(File file) throws IOException
      {
         File tempDir = OperatingSystemUtils.createTempDir();
         List<URL> result = new ArrayList<URL>();
         try (ZipFile zip = new ZipFile(file))
         {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
               ZipEntry entry = entries.nextElement();
               String name = entry.getName();
               if (name.matches(path + "/.*\\.jar"))
               {
                  log.log(Level.FINE, String.format("ZipEntry detected: %s len %d added %TD",
                           file.getAbsolutePath() + "/" + entry.getName(), entry.getSize(),
                           new Date(entry.getTime())));
                  try (InputStream input = JarLocator.class.getClassLoader().getResource(name).openStream())
                  {
                     result.add(copy(tempDir, entry.getName(), input).toURL());
                  }
               }
            }
         }
         catch (ZipException e)
         {
            throw new RuntimeException("Error handling file " + file, e);
         }
         return result;
      }

      private File copy(File targetDir, String name, InputStream input)
      {
         File outputFile = new File(targetDir, name);

         outputFile.getParentFile().mkdirs();

         try (FileOutputStream output = new FileOutputStream(outputFile))
         {
            final byte[] buffer = new byte[4096];
            int read = 0;
            while ((read = input.read(buffer)) != -1)
            {
               output.write(buffer, 0, read);
            }
            output.flush();
         }
         catch (Exception e)
         {
            throw new RuntimeException("Could not write out jar file " + name, e);
         }
         return outputFile;
      }
   }
}

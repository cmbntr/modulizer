package ch.cmbntr.modulizer.bootstrap.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import ch.cmbntr.modulizer.bootstrap.util.Resources.Pool;

public class ModulizerIO {

  private static final int FF_BITMASK = 0xFF;

  private ModulizerIO() {
    super();
  }

  public static URL copyStream(final URL src, final File dest) throws IOException, FileNotFoundException,
      MalformedURLException {
    final InputStream in = src.openStream();
    try {
      final FileOutputStream out = new FileOutputStream(dest);
      try {
        copy(in, out);
      } finally {
        closeQuietly(out);
      }
    } finally {
      closeQuietly(in);
    }
    return dest.toURI().toURL();
  }

  private static String baseName(final String path) {
    final File f = new File(path);
    final String n = f.getName();
    final int idx = n.lastIndexOf('.');
    return idx >= 0 ? n.substring(0, idx) : n;
  }

  private static String extension(final String name, final String ifNone) {
    final int idx = name.lastIndexOf('.');
    return idx >= 0 ? name.substring(idx + 1) : ifNone;
  }

  private static void copy(final InputStream input, final OutputStream output) throws IOException {
    final ReadableByteChannel in = inChannel(input);
    final WritableByteChannel out = outChannel(output);
    final Pool pool = Resources.getPoolHandle();
    final ByteBuffer buffer = pool.aquireBuffer();
    try {
      while (true) {
        if (in.read(buffer) == -1) {
          break;
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
          final int n = out.write(buffer);
          if (n < 1) {
            throw new IOException("write stalled");
          }
        }
        buffer.clear();
      }
    } finally {
      pool.releaseBuffer(buffer);
    }
  }

  private static ReadableByteChannel inChannel(final InputStream input) {
    if (input instanceof FileInputStream) {
      return ((FileInputStream) input).getChannel();
    } else {
      return Channels.newChannel(input);
    }
  }

  private static WritableByteChannel outChannel(final OutputStream output) {
    if (output instanceof FileOutputStream) {
      return ((FileOutputStream) output).getChannel();
    } else {
      return Channels.newChannel(output);
    }
  }

  public static synchronized void mkdir(final File dir) throws IOException {
    // if it exists, it must be a directory
    if (dir.exists()) {
      if (dir.isDirectory()) {
        return;
      } else {
        throw new IOException(dir + " already exists is not a directory");
      }
    }

    // create it
    final boolean created = dir.mkdirs();
    if (!created || !dir.isDirectory()) {
      throw new IOException("could not create dir " + dir);
    }
  }

  public static void closeQuietly(final Closeable closeable) {
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (final IOException e) {
      assert e != null;
    }
  }

  public static boolean verifySHA1Named(final File f) throws IOException {
    final StringBuilder sha1 = sha1(f);
    return sha1 != null && baseName(f.getName()).equals(sha1.toString());
  }

  public static String sha1Name(final File f) throws IOException {
    final StringBuilder sha1 = sha1(f);
    return sha1 == null ? null : sha1.append('.').append(extension(f.getName(), "dat")).toString();
  }

  public static LinkedHashMap<File, Future<String>> sha1async(final Iterable<File> files) {
    final LinkedHashMap<File, Future<String>> result = new LinkedHashMap<File, Future<String>>();
    final Pool pool = Resources.getPoolHandle();
    final ExecutorService exec = pool.aquireExec();
    try {
      for (final File f : files) {
        result.put(f, exec.submit(new Callable<String>() {
          @Override
          public String call() throws Exception {
            final StringBuilder sha1 = sha1(f);
            return sha1 == null ? null : sha1.toString();
          }
        }));
      }
      return result;
    } finally {
      pool.releaseExec(exec);
    }
  }

  public static StringBuilder sha1(final File f) throws IOException {
    if (f == null || !f.exists()) {
      return null;
    }
    return sha1(new FileInputStream(f).getChannel());
  }

  public static LinkedHashMap<URI, Future<String>> sha1URIasync(final Iterable<URI> uris) {
    final LinkedHashMap<URI, Future<String>> result = new LinkedHashMap<URI, Future<String>>();
    final Pool pool = Resources.getPoolHandle();
    final ExecutorService exec = pool.aquireExec();
    try {
      for (final URI f : uris) {
        result.put(f, exec.submit(new Callable<String>() {
          @Override
          public String call() throws Exception {
            final StringBuilder sha1 = sha1URI(f);
            return sha1 == null ? null : sha1.toString();
          }
        }));
      }
      return result;
    } finally {
      pool.releaseExec(exec);
    }
  }

  public static StringBuilder sha1URI(final URI uri) throws IOException {
    if (uri == null) {
      return null;
    }
    if ("file".equals(uri.getScheme())) {
      return sha1(new FileInputStream(new File(uri)).getChannel());
    } else {
      return sha1(Channels.newChannel(uri.toURL().openStream()));
    }
  }

  private static StringBuilder sha1(/* @WillClose */final ReadableByteChannel src) throws IOException {
    try {
      final Pool pool = Resources.getPoolHandle();
      final ByteBuffer buf = pool.aquireBuffer();
      try {
        final MessageDigest digest = pool.aquireDigest();
        try {
          int cnt = 0;
          while (cnt >= 0) {
            cnt = src.read(buf);
            buf.flip();
            digest.update(buf);
            buf.clear();
          }
          return formatAsHex(digest.digest());
        } finally {
          pool.releaseDigest(digest);
        }
      } finally {
        pool.releaseBuffer(buf);
      }
    } finally {
      closeQuietly(src);
    }
  }

  private static StringBuilder formatAsHex(final byte[] digestBytes) {
    final StringBuilder result = new StringBuilder(44);
    for (final byte b : digestBytes) {
      final String next = Integer.toHexString(b & FF_BITMASK);
      if (next.length() < 2) {
        result.append('0');
      }
      result.append(next);
    }
    return result;
  }

}

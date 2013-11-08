/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import outerbin.MService;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.ClassLoader.getSystemClassLoader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

class ResourceEntry {

    /**
     * Binary content of the resource.
     */
public byte[] binaryContent = null;
    /**
     * Loaded class.
     */
    public Class loadedClass = null;
    /**
     * URL of the codebase from where the object was loaded.
     */
    public URL codeBase = null;
    /**
     * Manifest (if the resource was loaded from a JAR).
     */
    public Manifest manifest = null;
    /**
     * Certificates (if the resource was loaded from a JAR).
     */
    public Certificate[] certificates = null;
}

class Loader extends URLClassLoader {

    private ClassLoader system = null;
    protected String[] repositories = new String[0];
    protected File[] files = new File[0];
    protected DirContext resources;
    protected Map<String, ResourceEntry> resourceEntries = new HashMap<String, ResourceEntry>();
    protected HashMap notFoundResources = new HashMap();
    /**
     * The list of JARs, in the order they should be searched for locally loaded
     * classes or resources.
     */
    protected JarFile[] jarFiles = new JarFile[0];
    /**
     * The list of JARs, in the order they should be searched for locally loaded
     * classes or resources.
     */
    protected File[] jarRealFiles = new File[0];
    /**
     * The path which will be monitored for added Jar files.
     */
    protected String jarPath = null;
    /**
     * The list of JARs, in the order they should be searched for locally loaded
     * classes or resources.
     */
    protected String[] jarNames = new String[0];
    /**
     * The set of optional packages (formerly standard extensions) that are
     * required in the repositories associated with this class loader. Each
     * object in this list is of type
     * <code>org.apache.catalina.loader.Extension</code>.
     */
    protected ArrayList required = new ArrayList();
    /**
     * The set of optional packages (formerly standard extensions) that are
     * available in the repositories associated with this class loader. Each
     * object in this list is of type
     * <code>org.apache.catalina.loader.Extension</code>.
     */
    protected ArrayList available = new ArrayList();

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public Loader() {
        super(new URL[0]);
        system = getSystemClassLoader();
    }

    public Class loadClass(String name, boolean resolve) throws ClassNotFoundException {

        Class clazz = null;

        try {
            clazz = system.loadClass(name);
            if (clazz != null) {
                System.out.println("system load class  " + name);
                if (resolve) {
                    resolveClass(clazz);
                }
                return (clazz);
            }
        } catch (ClassNotFoundException e) {
            // Ignore
        }

        try {
            clazz = findClass(name);
            if (clazz != null) {
                if (resolve) {
                    resolveClass(clazz);
                }
                return (clazz);
            }
        } catch (ClassNotFoundException e) {
        }

        // This class was not found
        throw new ClassNotFoundException(name);
    }

    private void log(String message, Throwable throwable) {
        System.out.println("Loader: " + message);
        throwable.printStackTrace(System.out);
    }

    private void log(String message) {
        System.out.println("Loader: " + message);
    }

    public Class findClass(String name) throws ClassNotFoundException {
        log("    findClass(" + name + ")");
        // Ask our superclass to locate this class, if possible
        // (throws ClassNotFoundException if it is not found)
        Class clazz = null;
        try {
            log("      findClassInternal(" + name + ")");
            try {
                clazz = findClassInternal(name);
            } catch (ClassNotFoundException cnfe) {
                throw cnfe;
            } catch (AccessControlException ace) {
                ace.printStackTrace();
                throw new ClassNotFoundException(name);
            } catch (RuntimeException e) {
                log("      -->RuntimeException Rethrown", e);
                throw e;
            }
            if (clazz == null) {
                try {
                    clazz = super.findClass(name);
                } catch (AccessControlException ace) {
                    throw new ClassNotFoundException(name);
                } catch (RuntimeException e) {
                    log("      -->RuntimeException Rethrown", e);
                    throw e;
                }
            }
            if (clazz == null) {
                log("    --> Returning ClassNotFoundException");
                throw new ClassNotFoundException(name);
            }
        } catch (ClassNotFoundException e) {
            log("    --> Passing on ClassNotFoundException", e);
            throw e;
        }

        // Return the class we have located
        log("      Returning class " + clazz);
        log("      Loaded by " + clazz.getClassLoader());
        return (clazz);

    }

    protected Class findClassInternal(String name)
            throws ClassNotFoundException {
        String tempPath = name.replace('.', '/');
        String path = tempPath + ".class";
        ResourceEntry entry = findResourceInternal(name, path);
        if(entry == null)
            return null;
        Class clazz = entry.loadedClass;
        if (clazz != null) {
            return clazz;
        }
        // Create the code source object
        CodeSource codeSource =
                new CodeSource(entry.codeBase, entry.certificates);
        if (entry.loadedClass == null) {
            synchronized (this) {
                if (entry.loadedClass == null) {
                    clazz = defineClass(name, entry.binaryContent, 0,
                            entry.binaryContent.length,
                            codeSource);
                    entry.loadedClass = clazz;
                } else {
                    clazz = entry.loadedClass;
                }
            }
        } else {
            clazz = entry.loadedClass;
        }
        return clazz;
    }

    protected ResourceEntry findResourceInternal(String name, String path) {
        ResourceEntry entry = (ResourceEntry) resourceEntries.get(name);
        if (entry != null) {
            return entry;
        }
        int contentLength = -1;
        InputStream binaryStream = null;

        int jarFilesLength = jarFiles.length;
        int repositoriesLength = repositories.length;
        int i;
        for (i = 0; (entry == null) && (i < repositoriesLength); i++) {
            String fullPath = repositories[i] + path;
            
            try {
                File file = new File(files[i], path);
                if (!file.exists()) {
                    continue;
                }
                
                entry = new ResourceEntry();
                entry.codeBase = getURL(file);
                InputStream in = new FileInputStream(file);
                entry.binaryContent = getClassContent(in);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        if (entry == null) {
            if (notFoundResources.containsKey(name)) {
                return null;
            }
            System.out.println("find class in jar");
            JarEntry jarEntry = null;
            for (i = 0; (entry == null) && (i < jarFilesLength); i++) {
                jarEntry = jarFiles[i].getJarEntry(path);

                if (jarEntry != null) {
                    System.out.println(jarFiles[i].getName());
                    entry = new ResourceEntry();
                    try {
                        entry.codeBase = getURL(jarRealFiles[i]);
                        String jarFakeUrl = entry.codeBase.toString();
                        jarFakeUrl = "jar:" + jarFakeUrl + "!/" + path;
                    } catch (MalformedURLException e) {
                        return null;
                    }
                    try {
                        entry.manifest = jarFiles[i].getManifest();
                        entry.binaryContent = getClassContent(jarFiles[i].getInputStream(jarEntry));
                    } catch (IOException e) {
                        return null;
                    }
                }

            }
        }


        if (entry == null) {
            synchronized (notFoundResources) {
                notFoundResources.put(name, name);
            }
            return null;
        }

        synchronized (resourceEntries) {
            // Ensures that all the threads which may be in a race to load
            // a particular class all end up with the same ResourceEntry
            // instance
            ResourceEntry entry2 = (ResourceEntry) resourceEntries.get(name);
            if (entry2 == null) {
                resourceEntries.put(name, entry);
            } else {
                entry = entry2;
            }
        }

        return entry;
    }

    private byte[] getClassContent(InputStream in) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        long count = 0;
        int n = 0;
        while (-1 != (n = in.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return output.toByteArray();
    }

    protected URL getURL(File file)
            throws MalformedURLException {

        File realFile = file;
        try {
            realFile = realFile.getCanonicalFile();
        } catch (IOException e) {
            // Ignore
        }

        //return new URL("file:" + realFile.getPath());
        return realFile.toURL();
    }

    public synchronized void addRepository(String repository, File file) {
        if (repository == null) {
            return;
        }

        System.out.println("addRepository(" + repository + ")");

        int i;

        // Add this repository to our internal list
        String[] result = new String[repositories.length + 1];
        for (i = 0; i < repositories.length; i++) {
            result[i] = repositories[i];
        }
        result[repositories.length] = repository;
        repositories = result;

        // Add the file to the list
        File[] result2 = new File[files.length + 1];
        for (i = 0; i < files.length; i++) {
            result2[i] = files[i];
        }
        result2[files.length] = file;
        files = result2;
    }

    synchronized void addJar(String jar, JarFile jarFile, File file)
            throws IOException {

        if (jar == null) {
            return;
        }
        if (jarFile == null) {
            return;
        }
        if (file == null) {
            return;
        }

        log("addJar(" + jar + ")");

        int i;

        if ((jarPath != null) && (jar.startsWith(jarPath))) {

            String jarName = jar.substring(jarPath.length());
            while (jarName.startsWith("/")) {
                jarName = jarName.substring(1);
            }

            String[] result = new String[jarNames.length + 1];
            for (i = 0; i < jarNames.length; i++) {
                result[i] = jarNames[i];
            }
            result[jarNames.length] = jarName;
            jarNames = result;

        }

        JarFile[] result2 = new JarFile[jarFiles.length + 1];
        for (i = 0; i < jarFiles.length; i++) {
            result2[i] = jarFiles[i];
        }
        result2[jarFiles.length] = jarFile;
        jarFiles = result2;

        // Add the file to the list
        File[] result4 = new File[jarRealFiles.length + 1];
        for (i = 0; i < jarRealFiles.length; i++) {
            result4[i] = jarRealFiles[i];
        }
        result4[jarRealFiles.length] = file;
        jarRealFiles = result4;

        // Load manifest
//        Manifest manifest = jarFile.getManifest();
//        if (manifest != null) {
//            Iterator extensions = Extension.getAvailable(manifest).iterator();
//            while (extensions.hasNext()) {
//                available.add(extensions.next());
//            }
//            extensions = Extension.getRequired(manifest).iterator();
//            while (extensions.hasNext()) {
//                required.add(extensions.next());
//            }
//        }

    }
}

/**
 *
 * @author ganqing
 */
public class Outer {

    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException {
        Loader loader = new Loader();
        String root = "d:/temp/outer";
        String classesPath = "/bin/";
        loader.addRepository("/bin/", new File(root + classesPath));

        String libPath = "/lib/";
        loader.setJarPath(libPath);
        File libDir = new File(root, libPath);
        if (libDir.isDirectory()) {
            File[] libs = libDir.listFiles();
            for (File jar : libs) {
                String filename = libPath + jar.getName();
                if (!filename.endsWith(".jar")) {
                    continue;
                }
                JarFile jarFile = new JarFile(jar);
                loader.addJar(filename, jarFile, jar);
            }
        }

        Class mainClass = loader.loadClass("outerbin.OuterBin", false);
        MService ps = (MService) mainClass.newInstance();
        ps.hello();
    }
}

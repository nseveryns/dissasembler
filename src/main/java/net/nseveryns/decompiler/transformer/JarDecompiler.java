package net.nseveryns.decompiler.transformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.nseveryns.decompiler.Project;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

public class JarDecompiler implements Transformer {
    @Override
    public Project createProject(File file) {
        try {
            JarFile jarFile = new JarFile(file);
            Enumeration<JarEntry> entries = jarFile.entries();
            Map<String, File> files = new HashMap<>();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                InputStream stream = jarFile.getInputStream(entry);
                byte[] buffer = IOUtils.toByteArray(stream);
                String name = FilenameUtils.getName(entry.getName());
                if (name.isEmpty()) {
                    name = entry.getName();
                }
                String suffix = FilenameUtils.getExtension(name);
                File cachedFile = File.createTempFile(name, suffix);

                FileOutputStream output = new FileOutputStream(cachedFile);
                output.write(buffer);
                files.put(name, cachedFile);
            }
            return new Project(file.getName(), files);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void decompile(File file, Consumer<String> consumer) {
        try {
            JarFile jarFile = new JarFile(file);
            Enumeration<JarEntry> entries = jarFile.entries();
            StringBuilder builder = new StringBuilder();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                builder.append(entry.getName());
                builder.append("\n");
            }
            consumer.accept(builder.toString());
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
        consumer.accept("Invalid file.");
    }

    @Override
    public boolean acceptFile(File file) {
        return FilenameUtils.getExtension(file.getName()).equals("jar");
    }
}

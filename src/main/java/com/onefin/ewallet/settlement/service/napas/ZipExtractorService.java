package com.onefin.ewallet.settlement.service.napas;

import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class ZipExtractorService {

    public void extractZipFile(String zipFilePath, String targetDirectory) throws IOException, ArchiveException {
        File zipFile = new File(zipFilePath);
        try (InputStream is = Files.newInputStream(zipFile.toPath());
             ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, is)) {

            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (!ais.canReadEntryData(entry)) {
                    continue;
                }

                String entryName = entry.getName();
                String entryPath = targetDirectory + File.separator + entryName;

                if (entry.isDirectory()) {
                    // Create directories if they don't exist
                    new File(entryPath).mkdirs();
                } else {
                    // Extract the file
                    try (OutputStream os = Files.newOutputStream(Paths.get(entryPath))) {
                        IOUtils.copy(ais, os);
                    }
                }
            }
        }
    }

    public void compressToZipFile(String sourceFilePath, String zipFilePath) throws IOException {
        try (
                FileOutputStream fos = new FileOutputStream(zipFilePath);
                ArchiveOutputStream aos = new ArchiveStreamFactory()
                        .createArchiveOutputStream(ArchiveStreamFactory.ZIP, fos)
        ) {
            File sourceFile = new File(sourceFilePath);

            if (sourceFile.isFile()) {
                String entryName = sourceFile.getName();
                ZipArchiveEntry zipEntry = new ZipArchiveEntry(entryName);
                aos.putArchiveEntry(zipEntry);

                try (FileInputStream fis = new FileInputStream(sourceFile)) {
                    IOUtils.copy(fis, aos);
                } finally {
                    aos.closeArchiveEntry();
                }
            }
        } catch (ArchiveException e) {
            throw new RuntimeException(e);
        }
    }
}

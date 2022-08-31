package top.code2life.config;

import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static top.code2life.config.ConfigurationUtils.CONFIG_FILE_PREFIX;
import static top.code2life.config.ConfigurationUtils.trimRelativePathAndReplaceBackSlash;

/**
 * Directory WatchService target, could be file or directory
 *
 * @author Code2Life
 */
@Data
public class FileSystemWatchTarget {

    private WatchTargetType type;

    private String normalizedDir;

    private List<String> filterFiles;

    private Path rootDir;

    FileSystemWatchTarget(WatchTargetType type, String originalPath) {
        this.type = type;
        if (originalPath.startsWith(CONFIG_FILE_PREFIX)) {
            originalPath = trimRelativePathAndReplaceBackSlash(originalPath.substring(CONFIG_FILE_PREFIX.length()));
        } else {
            originalPath = trimRelativePathAndReplaceBackSlash(originalPath);
        }

        if (type == WatchTargetType.CONFIG_LOCATION) {
            this.normalizedDir = originalPath;
        } else if (type == WatchTargetType.CONFIG_IMPORT_FILE) {
            int idx = originalPath.lastIndexOf("/");
            this.normalizedDir = originalPath.substring(0, idx);
            this.filterFiles = new ArrayList<>(2);
            this.filterFiles.add(originalPath.substring(idx + 1));
        } else if (type == WatchTargetType.CONFIG_IMPORT_TREE) {
            this.normalizedDir = originalPath;
        }
    }

    /**
     * The watch target type, from spring.config.location or import:configtree / file
     */
    public enum WatchTargetType {

        /**
         * When using spring.config.location
         */
        CONFIG_LOCATION,

        /**
         * When using spring.config.import=file:
         */
        CONFIG_IMPORT_FILE,

        /**
         * When using spring.config.import=configtree:
         */
        CONFIG_IMPORT_TREE
    }
}

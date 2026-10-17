package com.campus.charity.module.admin.service;
import com.campus.charity.common.config.BackupProperties;
import com.campus.charity.common.config.FileProperties;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
/** 数据库与原件一起备份；仅清理本组件目录中本系统命名的过期备份，绝不删除业务原件。 */
@Component
public class BackupArchive {
    private final BackupProperties config;private final FileProperties files;
    public BackupArchive(BackupProperties config,FileProperties files){this.config=config;this.files=files;}
    public File pack(File sql) throws IOException {
        Path destination=sql.toPath().resolveSibling(sql.getName().replace(".sql",".zip"));
        Path upload=Paths.get(files.getUploadPath()).toAbsolutePath().normalize();
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination))){
            zip.putNextEntry(new ZipEntry("database/"+sql.getName()));Files.copy(sql.toPath(),zip);zip.closeEntry();
            if(Files.isDirectory(upload))try(Stream<Path> paths=Files.walk(upload)){
                for(Path p:(Iterable<Path>)paths.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS))::iterator){
                    if(!p.toAbsolutePath().normalize().startsWith(upload))throw new IOException("备份路径超出原件目录");
                    zip.putNextEntry(new ZipEntry("upload/"+upload.relativize(p).toString().replace('\\','/')));Files.copy(p,zip);zip.closeEntry();
                }
            }
        }
        Path dir=sql.getParentFile().toPath().toAbsolutePath().normalize();Instant cutoff=Instant.now().minus(Math.max(1,config.getRetentionDays()),ChronoUnit.DAYS);
        try(Stream<Path> paths=Files.list(dir)){
            for(Path p:(Iterable<Path>)paths::iterator){String name=p.getFileName().toString();if(p.toAbsolutePath().normalize().startsWith(dir)&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&name.startsWith(config.getDatabase()+"_")&&(name.endsWith(".sql")||name.endsWith(".zip"))&&Files.getLastModifiedTime(p).toInstant().isBefore(cutoff))Files.delete(p);}
        }
        return destination.toFile();
    }
}

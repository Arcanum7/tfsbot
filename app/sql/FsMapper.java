package sql;

import model.TFile;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Danilin | denis@danilin.name
 * 01.05.2020
 * tfs ☭ sweat and blood
 */
public interface FsMapper {
    void createUserFs(@Param("owner") long owner);
    void createRoot(@Param("owner") long owner);
    void createTree(@Param("owner") long owner);

    int mkDir(
            @Param("name") String name,
            @Param("parentId") long parentId,
            @Param("indate") long indate,
            @Param("type") String type,
            @Param("owner") long owner
            );

    void mkFile(@Param("file") TFile file, @Param("owner") long owner);

    TFile getEntry(@Param("id") long id, @Param("owner") long owner);
    TFile findEntryByPath(@Param("path") String path, @Param("owner") long owner);
    TFile findEntryAt(@Param("name") String name, @Param("parentId") long parentId, @Param("owner") long owner);
    List<TFile> listEntries(@Param("parentId") long dirId, @Param("owner") long id);

    void updateEntry(@Param("name") String name, @Param("indate") long indate, @Param("parentId") long parentId, @Param("id") long id, @Param("owner") long owner);

    void dropEntry(@Param("id") long id, @Param("owner") long owner);
    void dropOrphans(@Param("id") long id, @Param("owner") long owner);

    boolean isFsTableExist(long userId);

    void updateDirCount(@Param("size") long size, @Param("id") long id, @Param("owner") long owner);

    TFile getParentEntry(@Param("id") long id, @Param("owner") long owner);

    void dropEntryByPath(@Param("path") String path, @Param("owner") long owner);

    void dropEntryByName(@Param("name") String name, @Param("parentId") long parentId, @Param("owner") long owner);

    List<TFile> findEntriesByPaths(@Param("paths") Collection<String> paths, @Param("owner") long owner);

    void dropEntries(@Param("ids") Collection<Long> ids, @Param("owner") long owner);
    void dropMultiOrphans(@Param("ids") Collection<Long> ids, @Param("owner") long owner);

    void mkDirs(@Param("dirs") Collection<TFile> dirs, @Param("owner") long owner);
}
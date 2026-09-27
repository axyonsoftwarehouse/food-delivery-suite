package com.foodie.api.storage;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class FileRepository {
    private final JdbcTemplate jdbc;

    public FileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record FileRecord(long id, String storage, String path, String originalName, String contentType,
                             long byteSize, String checksum, Long uploadedBy, String purpose, String createdAt) {}

    public long insert(String storage, String path, String originalName, String contentType, long byteSize,
                       String checksum, Long uploadedBy, String purpose) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO files (storage, path, original_name, content_type, byte_size, checksum, uploaded_by, purpose) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, storage);
            statement.setString(2, path);
            statement.setString(3, originalName == null ? "" : originalName);
            statement.setString(4, contentType);
            statement.setLong(5, byteSize);
            statement.setString(6, checksum);
            if (uploadedBy == null) statement.setNull(7, Types.BIGINT); else statement.setLong(7, uploadedBy);
            statement.setString(8, purpose == null ? "other" : purpose);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public Optional<FileRecord> find(long id) {
        List<FileRecord> rows = jdbc.query(
            "SELECT id, storage, path, original_name, content_type, byte_size, checksum, uploaded_by, purpose, created_at FROM files WHERE id = ?",
            (rs, row) -> {
                long uploader = rs.getLong("uploaded_by");
                boolean uploaderNull = rs.wasNull();
                return new FileRecord(rs.getLong("id"), rs.getString("storage"), rs.getString("path"), rs.getString("original_name"),
                    rs.getString("content_type"), rs.getLong("byte_size"), rs.getString("checksum"), uploaderNull ? null : uploader,
                    rs.getString("purpose"), rs.getTimestamp("created_at").toInstant().toString());
            },
            id
        );
        return rows.stream().findFirst();
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM files WHERE id = ?", id);
    }
}

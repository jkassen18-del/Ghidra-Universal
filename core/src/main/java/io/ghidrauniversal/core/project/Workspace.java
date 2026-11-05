package io.ghidrauniversal.core.project;

import io.ghidrauniversal.core.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Filesystem-backed store of {@link GuProject}s. Not a database — just a root
 * directory with one subdirectory per project. Thread-safety is coarse: callers
 * should treat a single project's lifecycle as owned by one worker at a time.
 */
public final class Workspace {

    private final Path root;

    public Workspace(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Default workspace under the user's home: {@code ~/.ghidra-universal}. */
    public static Workspace defaultWorkspace() {
        return new Workspace(Path.of(System.getProperty("user.home"), ".ghidra-universal"));
    }

    public Path root() {
        return root;
    }

    /**
     * Import a binary into a new project directory, copying the file in.
     * The returned project has status {@link GuProject.Status#IMPORTED}.
     */
    public GuProject importBinary(Path binary, String displayName) {
        try {
            String id = UUID.randomUUID().toString();
            String fileName = binary.getFileName().toString();
            String sha = sha256(binary);
            GuProject project = new GuProject(
                    id,
                    displayName != null ? displayName : fileName,
                    fileName,
                    sha,
                    System.currentTimeMillis(),
                    GuProject.Status.IMPORTED);

            Files.createDirectories(project.binaryDir(root));
            Files.createDirectories(project.ghidraDir(root));
            Files.copy(binary, project.binaryDir(root).resolve(fileName));
            save(project);
            return project;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save(GuProject project) {
        try {
            Files.createDirectories(project.dir(root));
            Files.writeString(project.metadataJson(root), Json.write(project));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<GuProject> find(String id) {
        Path meta = root.resolve("projects").resolve(id).resolve("project.json");
        if (!Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        return Optional.of(Json.read(meta, GuProject.class));
    }

    public List<GuProject> list() {
        Path projects = root.resolve("projects");
        if (!Files.isDirectory(projects)) {
            return List.of();
        }
        List<GuProject> out = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(projects)) {
            dirs.filter(Files::isDirectory).forEach(d -> {
                Path meta = d.resolve("project.json");
                if (Files.isRegularFile(meta)) {
                    out.add(Json.read(meta, GuProject.class));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(Comparator.comparingLong(GuProject::createdEpochMs).reversed());
        return out;
    }

    /** Path to the imported binary file for a project. */
    public Path binaryFile(GuProject project) {
        return project.binaryDir(root).resolve(project.binaryFileName());
    }

    private static String sha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(file));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("SHA-256 failed for " + file, e);
        }
    }
}

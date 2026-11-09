package org.codingburgas.ChemicalRecipeSystem.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.Permissions;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.errors.ForbiddenException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.Attachment;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.repositories.AttachmentRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Files on recipes: PDF/PNG/JPG up to 5 MB, checked by extension AND file signature, stored under a random name. */
@Service
@Transactional
public class AttachmentService {
    public record AttachmentDto(Long id, String name, long size, String uploadedBy, LocalDateTime at, boolean mine) {
    }

    public record Download(Path path, String name, String contentType) {
    }

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of("pdf", "application/pdf", "png", "image/png",
        "jpg", "image/jpeg", "jpeg", "image/jpeg");

    private final AttachmentRepository repo;
    private final RecipeRepository recipes;
    private final AuditService audit;
    private final Path dir;

    public AttachmentService(AttachmentRepository repo, RecipeRepository recipes, AuditService audit,
                             @Value("${crms.upload-dir:data/uploads}") String dir) {
        this.repo = repo;
        this.recipes = recipes;
        this.audit = audit;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public void upload(Long recipeId, MultipartFile file, UserDto actor) throws IOException {
        Recipe recipe = recipes.findByIdAndDeletedAtIsNull(recipeId).orElseThrow(() -> new NotFoundException("Recipe not found."));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file first.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("The file is larger than 5 MB.");
        }
        String name = clean(file.getOriginalFilename());
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!TYPES.containsKey(ext) || !signatureMatches(ext, file)) {
            throw new IllegalArgumentException("Only real PDF, PNG and JPG files are allowed.");
        }
        String stored = UUID.randomUUID() + "." + ext;
        Files.createDirectories(dir);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(stored));
        }
        Attachment saved = repo.save(new Attachment(recipe, name, stored, TYPES.get(ext), file.getSize(), actor.getId(), actor.getFullName()));
        audit.log("UPLOAD", "Recipe", recipeId, name);
        notifyNothing(saved);
    }

    private static void notifyNothing(Attachment a) {
        // placeholder kept tiny on purpose: uploads are audited, not notified
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> list(Long recipeId, UserDto actor) {
        return repo.findByRecipeIdOrderByCreatedAtDesc(recipeId).stream()
            .map(a -> new AttachmentDto(a.getId(), a.getOriginalName(), a.getSize(), a.getUploadedByName(), a.getCreatedAt(),
                canAccess(a, actor))).toList();
    }

    /** Only the uploader and editors/admins (RECIPE_WRITE) may open a file. */
    @Transactional(readOnly = true)
    public Download download(Long id, UserDto actor) {
        Attachment a = repo.findById(id).orElseThrow(() -> new NotFoundException("File not found."));
        if (a.getRecipe().isDeleted()) {
            throw new NotFoundException("File not found.");
        }
        if (!canAccess(a, actor)) {
            throw new ForbiddenException("You may not open files uploaded by other people.");
        }
        Path path = dir.resolve(a.getStoredName()).normalize();
        if (!path.startsWith(dir) || !Files.exists(path)) {
            throw new NotFoundException("File not found.");
        }
        return new Download(path, a.getOriginalName(), a.getContentType());
    }

    public void delete(Long id, UserDto actor) throws IOException {
        Attachment a = repo.findById(id).orElseThrow(() -> new NotFoundException("File not found."));
        if (!a.getUploadedById().equals(actor.getId()) && !Permissions.has(actor.getRole(), Permission.USER_ADMIN)) {
            throw new ForbiddenException("Only the uploader or an administrator can delete this file.");
        }
        Files.deleteIfExists(dir.resolve(a.getStoredName()));
        repo.delete(a);
        audit.log("DELETE_FILE", "Recipe", a.getRecipe().getId(), a.getOriginalName());
    }

    public void deleteAllFor(Long recipeId) {
        for (Attachment a : repo.findByRecipeIdOrderByCreatedAtDesc(recipeId)) {
            try {
                Files.deleteIfExists(dir.resolve(a.getStoredName()));
            } catch (IOException ignored) {
                // an orphan file is harmless, the row is what matters
            }
            repo.delete(a);
        }
    }

    private static boolean canAccess(Attachment a, UserDto actor) {
        return a.getUploadedById().equals(actor.getId()) || Permissions.has(actor.getRole(), Permission.RECIPE_WRITE);
    }

    private static String clean(String original) {
        String name = original == null ? "file" : Path.of(original.replace('\\', '/')).getFileName().toString();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.length() > 100 ? name.substring(name.length() - 100) : name;
    }

    private static boolean signatureMatches(String ext, MultipartFile file) throws IOException {
        byte[] head = new byte[4];
        try (InputStream in = file.getInputStream()) {
            if (in.read(head) < 3) {
                return false;
            }
        }
        return switch (ext) {
            case "pdf" -> head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            default -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
        };
    }
}

package site.devtown.spadeworker.domain.file.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import site.devtown.spadeworker.domain.file.constant.ImageFileType;
import site.devtown.spadeworker.domain.file.exception.ImageFileNotFoundException;
import site.devtown.spadeworker.global.util.ImageUtil;

import java.io.File;
import java.util.Objects;
import java.util.UUID;

@Service
public class LocalImageFileService
        implements ImageFileService {

    @Value("${image.local-image-storage-location}")
    private String localFileStorageLocation;
    @Value("${image.project-thumbnail-image.default-image-name}")
    private String localStorageDefaultProjectThumbnailImageName;
    @Value("${image.project-thumbnail-image.default-image-uri}")
    private String localStorageDefaultProjectThumbnailImageUri;

    @Override
    public String saveImage(
            ImageFileType imageFileType,
            MultipartFile fileData
    ) throws Exception {
        String localStorageDefaultImageUri = "";

        switch (imageFileType) {
            case PROJECT_THUMBNAIL_IMAGE -> {
                localStorageDefaultImageUri = localStorageDefaultProjectThumbnailImageUri;
            }
        }

        return !fileData.isEmpty() ?
                uploadFile(imageFileType, fileData) :
                localStorageDefaultImageUri;
    }

    @Override
    public String updateImage(
            ImageFileType imageFileType,
            MultipartFile fileData,
            String savedImageUri
    ) throws Exception {
        String savedImageName = ImageUtil.getLocalStorageImageName(savedImageUri);
        String requestImageName = fileData.getOriginalFilename();
        String localStorageDefaultImageUri = "";
        String localStorageDefaultImageName = "";

        switch (imageFileType) {
            case PROJECT_THUMBNAIL_IMAGE -> {
                localStorageDefaultImageUri = localStorageDefaultProjectThumbnailImageUri;
                localStorageDefaultImageName = localStorageDefaultProjectThumbnailImageName;
            }
        }

        // 사용자가 기본 이미지를 선택한 경우
        if (fileData.isEmpty()) {
            // 현재 저장된 이미지가 기본 이미지가 아닌 경우
            if (!savedImageName.equals(localStorageDefaultImageName))
                deleteFile(savedImageUri);

            return localStorageDefaultImageUri;

        } else if (!requestImageName.equals(savedImageName)) {  // 사용자가 이미지 변경을 요청했을 경우
            // 기존 이미지가 기본 이미지가 아닐 경우에만 삭제
            if (!savedImageName.equals(localStorageDefaultImageName))
                deleteFile(savedImageUri);

            return uploadFile(
                    imageFileType,
                    fileData
            );
        }

        // 이미지가 변경되지 않았을 경우
        return savedImageUri;
    }

    @Override
    public String uploadFile(
            ImageFileType imageFileType,
            MultipartFile fileData
    ) throws Exception {
        String storedFileName = getStoredFileName(
                imageFileType,
                Objects.requireNonNull(fileData.getOriginalFilename())
        );
        String fileStoredFullPath = getFileStoredFullPath(
                imageFileType,
                storedFileName
        );

        // 파일 업로드
        fileData.transferTo(new File(fileStoredFullPath));

        return fileStoredFullPath;
    }

    @Override
    public String getStoredFileName(
            ImageFileType imageFileType,
            String originalFileName
    ) {
        String uuid = UUID.randomUUID().toString();
        String extension = originalFileName
                .substring(originalFileName.lastIndexOf(".") + 1);

        return imageFileType.getImageType() + "_" + uuid + "." + extension;
    }

    @Override
    public String getFileStoredFullPath(
            ImageFileType imageFileType,
            String storedFileName
    ) {
        return localFileStorageLocation + "/" + imageFileType.getImageType() + "/" + storedFileName;
    }

    @Override
    public void deleteFile(String fileStoredFullPath) {
        File deleteFile = new File(fileStoredFullPath);

        System.out.println(fileStoredFullPath);

        // 삭제할 파일이 존재하는지 검증
        if (!deleteFile.exists()) {
            throw new ImageFileNotFoundException();
        }

        deleteFile.delete();
    }
}
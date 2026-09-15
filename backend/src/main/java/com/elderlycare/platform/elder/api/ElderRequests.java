package com.elderlycare.platform.elder.api;

import com.elderlycare.platform.elder.domain.ElderProfileData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class ElderRequests {
    private ElderRequests() {}
    public record Create(@NotNull UUID requestId, @NotNull @Valid ElderProfileData profile) {}
    public record Update(@NotNull @Min(0) Long version, @NotNull @Valid ElderProfileData profile) {}
    public record Transition(@NotNull @Min(0) Long version) {}
}

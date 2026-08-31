package app.nanotes.backend.web.dto;

import java.util.List;

public record SharesDto(List<UserShareDto> userShares, PublicShareDto publicShare) {}

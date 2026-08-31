package app.nanotes.backend.web.dto;

public record ErrorDto(Detail error) {
    public record Detail(String code, String message) {}

    public static ErrorDto of(String code, String message) {
        return new ErrorDto(new Detail(code, message));
    }
}

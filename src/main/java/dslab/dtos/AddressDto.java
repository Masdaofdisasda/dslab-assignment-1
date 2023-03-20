package dslab.dtos;

public class AddressDto {
    private String host;
    private String port;
    private String email;

    public AddressDto(String host, String port, String email) {
        this.host = host;
        this.port = port;
        this.email = email;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

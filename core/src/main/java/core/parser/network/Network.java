package core.parser.network;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import javax.persistence.ElementCollection;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Network implements Serializable {

    private final static long serialVersionUID = 5116414984211561420L;

    @SerializedName("network")
    @Expose
    @javax.persistence.Id
    @NotEmpty(message = "Network name must not be empty.")
    private String network;

    @SerializedName("sites")
    @Expose
    @NotEmpty(message = "sites must not be empty")
    @ElementCollection
    private Set<@Valid Site> sites = null;

    /**
     * No args constructor for use in serialization
     */
    public Network() {
    }

    public Network(String network, Set<Site> sites) {
        super();
        this.network = network;
        this.sites = sites;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public Set<Site> getSites() {
        return sites;
    }

    public boolean addSite(Site site) {
        return this.sites.add(site);
    }

    public boolean deleteSite(String site_name) {
        return sites.removeIf(site -> site.getSiteName().equals(site_name));
    }

    public Set<AvailablePlatform> getPlatforms() {
        // Return one set with one available platform
        Set<AvailablePlatform> availablePlatforms = new HashSet<>();
        availablePlatforms.add(new AvailablePlatform("platform_0"));
        return availablePlatforms;
//        return this.sites.stream()
//                .flatMap(site -> site.getAvailablePlatforms().stream())
//                .collect(Collectors.toSet());
    }
}

package org.exoplatform.commons.search.service;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.RuntimeDelegate;

import org.exoplatform.commons.api.search.SearchService;
import org.exoplatform.commons.api.search.SearchServiceConnector;
import org.exoplatform.commons.api.search.data.SearchContext;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.impl.RuntimeDelegateImpl;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.settings.impl.SettingServiceImpl;
import org.exoplatform.web.WebAppController;
import org.exoplatform.web.controller.metadata.ControllerDescriptor;
import org.exoplatform.web.controller.metadata.DescriptorBuilder;
import org.exoplatform.web.controller.router.Router;

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
public class UnifiedSearchService implements ResourceContainer {
  private final static Log LOG = ExoLogger.getLogger(UnifiedSearchService.class);
  
  private static final CacheControl cacheControl;
  static {
    RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
    cacheControl = new CacheControl();
    cacheControl.setNoCache(true);
    cacheControl.setNoStore(true);    
  }
  
  private static SearchSetting defaultSearchSetting = new SearchSetting(10, Arrays.asList("all"), false, false, false);
  private static SearchSetting anonymousSearchSetting = new SearchSetting(10, Arrays.asList("page", "file", "document", "post"), true, false, true);
  private static SearchSetting defaultQuicksearchSetting = new SearchSetting(5, Arrays.asList("all"), true, true, true);
  
  private SearchService searchService;
  private UserPortalConfigService userPortalConfigService;
  private SettingService settingService;
  private Router router;
  
  public UnifiedSearchService(SearchService searchService, SettingService settingService, UserPortalConfigService userPortalConfigService, WebAppController webAppController){
    this.searchService = searchService;
    this.settingService = settingService;
    this.userPortalConfigService = userPortalConfigService;
    
    try {
      File controllerXml = new File(webAppController.getConfigurationPath());
      URL url = controllerXml.toURI().toURL();
      ControllerDescriptor routerDesc = new DescriptorBuilder().build(url.openStream());
      this.router = new Router(routerDesc);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
    }
    
  }
  
  /**
   * Search for a query, with (optional) parameters (sites, content types...)
   * 
   * @param context Search context
   * @param query The user-input query to search for
   * @param sites Search on these specified sites only (e.g acme, intranet...)
   * @param types Search for these specified content types only (e.g people, discussion, event, task, wiki, activity, social, file, document...)
   * @param offset Start offset of the result set
   * @param limit Maximum size of the result set 
   * @param sort Sort type (relevancy, date, title)
   * @param order Sort order (asc, desc)
   * 
   * @return a map of connector with their search result
   * 
   * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.search
   */
  @GET
  public Response REST_search(
      @javax.ws.rs.core.Context UriInfo uriInfo,
      @QueryParam("q") String query, 
      @QueryParam("sites") @DefaultValue("all") String sSites, 
      @QueryParam("types") String sTypes, 
      @QueryParam("offset") @DefaultValue("0") String sOffset, 
      @QueryParam("limit") String sLimit, 
      @QueryParam("sort") @DefaultValue("relevancy") String sort, 
      @QueryParam("order") @DefaultValue("desc") String order) 
  {
    try {
      MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
      String siteName = queryParams.getFirst("searchContext[siteName]");
      SearchContext context = new SearchContext(this.router, siteName);
      
      if(null==query || query.isEmpty()) return Response.ok("", MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  
      String userId = ConversationState.getCurrent().getIdentity().getUserId();
      boolean isAnonymous = null==userId || userId.isEmpty() || userId.equals("__anonim");
      SearchSetting searchSetting = isAnonymous ? anonymousSearchSetting : getSearchSetting();
      
      List<String> sites = Arrays.asList(sSites.split(",\\s*"));      
      if(sites.contains("all")) sites = userPortalConfigService.getAllPortalNames();      
      List<String> types = isAnonymous||null==sTypes ? searchSetting.getSearchTypes() : Arrays.asList(sTypes.split(",\\s*"));
      int offset = Integer.parseInt(sOffset);
      int limit = isAnonymous||null==sLimit||sLimit.isEmpty() ? (int)searchSetting.getResultsPerPage() : Integer.parseInt(sLimit);

      Map<String, Collection<SearchResult>> results = searchService.search(context, query, sites, types, offset, limit, sort, order);
      
      // get the base URI - http://<host>:<port>
      String baseUri = uriInfo.getBaseUri().toString(); // http://<host>:<port>/rest
      baseUri = baseUri.substring(0, baseUri.lastIndexOf("/"));
      String resultUrl, imageUrl;      
      
      // use absolute path for URLs in search results
      for(Collection<SearchResult> connectorResults:results.values()){
        for(SearchResult result:connectorResults){
          resultUrl = result.getUrl();
          imageUrl =  result.getImageUrl();
          if(null!=resultUrl && resultUrl.startsWith("/")) result.setUrl(baseUri + resultUrl);
          if(null!=imageUrl && imageUrl.startsWith("/")) result.setImageUrl(baseUri + imageUrl);          
        }        
      }
      
      return Response.ok(results, MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return Response.serverError().status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).cacheControl(cacheControl).build();
    }
  }

  /**
  * Get all connectors registered in the system and which are enabled
  *
  * @return List of connectors and names of the enabled ones (in JSON)
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.registry
  */    
  @GET
  @Path("/registry")
  public Response REST_getRegistry() {
    LinkedHashMap<String, SearchServiceConnector> searchConnectors = new LinkedHashMap<String, SearchServiceConnector>();
    for(SearchServiceConnector connector:searchService.getConnectors()) {
      searchConnectors.put(connector.getSearchType(), connector);
    }
    return Response.ok(Arrays.asList(searchConnectors, getEnabledSearchTypes()), MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  }

  
  /**
  * Get all available sites in the system
  *
  * @return List of site names in JSON
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.sites
  */  
  @GET
  @Path("/sites")
  public Response REST_getSites() {
    try {
      return Response.ok(userPortalConfigService.getAllPortalNames(), MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return Response.serverError().status(Response.Status.INTERNAL_SERVER_ERROR).entity(e).cacheControl(cacheControl).build();
    }
  }

  
  @SuppressWarnings("unchecked")
  private SearchSetting getSearchSetting() {
    try {
      Long resultsPerPage = ((SettingValue<Long>)settingService.get(Context.USER, Scope.WINDOWS, "resultsPerPage")).getValue();
      String searchTypes = ((SettingValue<String>) settingService.get(Context.USER, Scope.WINDOWS, "searchTypes")).getValue();
      Boolean searchCurrentSiteOnly = ((SettingValue<Boolean>) settingService.get(Context.USER, Scope.WINDOWS, "searchCurrentSiteOnly")).getValue();
      Boolean hideSearchForm = ((SettingValue<Boolean>) settingService.get(Context.USER, Scope.WINDOWS, "hideSearchForm")).getValue();
      Boolean hideFacetsFilter = ((SettingValue<Boolean>) settingService.get(Context.USER, Scope.WINDOWS, "hideFacetsFilter")).getValue();
      
      return new SearchSetting(resultsPerPage, Arrays.asList(searchTypes.split(",\\s*")), searchCurrentSiteOnly, hideSearchForm, hideFacetsFilter);
    } catch (Exception e) {
      return defaultSearchSetting;
    }
  }

  /**
  * Get current user's setting for Search portlet
  *
  * @return Search setting of the current logging in (or anonymous) user 
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.setting
  */    
  @GET
  @Path("/setting")
  public Response REST_getSearchSetting() {
    String userId = ConversationState.getCurrent().getIdentity().getUserId();
    return Response.ok(userId.equals("__anonim") ? anonymousSearchSetting : getSearchSetting(), MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  }
  
  /**
  * Save current user's setting for Search portlet
  *
  * @return "ok" if succeed
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.setting.post
  */    
  @POST
  @Path("/setting")
  public Response REST_setSearchSetting(@FormParam("resultsPerPage") long resultsPerPage, @FormParam("searchTypes") String searchTypes, @FormParam("searchCurrentSiteOnly") boolean searchCurrentSiteOnly, @FormParam("hideSearchForm") boolean hideSearchForm, @FormParam("hideFacetsFilter") boolean hideFacetsFilter) {
    settingService.set(Context.USER, Scope.WINDOWS, "resultsPerPage", new SettingValue<Long>(resultsPerPage));
    settingService.set(Context.USER, Scope.WINDOWS, "searchTypes", new SettingValue<String>(searchTypes));
    settingService.set(Context.USER, Scope.WINDOWS, "searchCurrentSiteOnly", new SettingValue<Boolean>(searchCurrentSiteOnly));
    settingService.set(Context.USER, Scope.WINDOWS, "hideSearchForm", new SettingValue<Boolean>(hideSearchForm));
    settingService.set(Context.USER, Scope.WINDOWS, "hideFacetsFilter", new SettingValue<Boolean>(hideFacetsFilter));
    
    return Response.ok("ok", MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  } 


  @SuppressWarnings("unchecked")
  private SearchSetting getQuickSearchSetting() {
    try {
      Long resultsPerPage = ((SettingValue<Long>)settingService.get(Context.GLOBAL, Scope.WINDOWS, "resultsPerPage")).getValue();
      String searchTypes = ((SettingValue<String>) settingService.get(Context.GLOBAL, Scope.WINDOWS, "searchTypes")).getValue();
      Boolean searchCurrentSiteOnly = ((SettingValue<Boolean>) settingService.get(Context.GLOBAL, Scope.WINDOWS, "searchCurrentSiteOnly")).getValue();
      
      return new SearchSetting(resultsPerPage, Arrays.asList(searchTypes.split(",\\s*")), searchCurrentSiteOnly, true, true);
    } catch (Exception e) {
      return defaultQuicksearchSetting;
    }
  }

  /**
  * Get current user's setting for Quick search portlet
  *
  * @return Quick search setting of the current logging in user 
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.quicksearchsetting
  */    
  @GET
  @Path("/setting/quicksearch")
  public Response REST_getQuicksearchSetting() {
    return Response.ok(getQuickSearchSetting(), MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  }
  
  /**
  * Save current user's setting for Quick search portlet
  *
  * @return "ok" if succeed
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.quicksearchsetting.post
  */    
  @POST
  @Path("/setting/quicksearch")
  public Response REST_setQuicksearchSetting(@FormParam("resultsPerPage") long resultsPerPage, @FormParam("searchTypes") String searchTypes, @FormParam("searchCurrentSiteOnly") boolean searchCurrentSiteOnly) {
    settingService.set(Context.GLOBAL, Scope.WINDOWS, "resultsPerPage", new SettingValue<Long>(resultsPerPage));
    settingService.set(Context.GLOBAL, Scope.WINDOWS, "searchTypes", new SettingValue<String>(searchTypes));
    settingService.set(Context.GLOBAL, Scope.WINDOWS, "searchCurrentSiteOnly", new SettingValue<Boolean>(searchCurrentSiteOnly));
    return Response.ok("ok", MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  } 
  
  
  @SuppressWarnings("unchecked")
  public static List<String> getEnabledSearchTypes(){
    SettingService settingService = (SettingServiceImpl)ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(SettingServiceImpl.class);
    SettingValue<String> enabledSearchTypes = (SettingValue<String>) settingService.get(Context.GLOBAL, Scope.APPLICATION, "enabledSearchTypes");
    if(null!=enabledSearchTypes) return Arrays.asList(enabledSearchTypes.getValue().split(",\\s*"));

    SearchService searchService = (SearchService)ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(SearchService.class);
    LinkedList<String> allSearchTypes = new LinkedList<String>();
    for(SearchServiceConnector connector:searchService.getConnectors()) {
      allSearchTypes.add(connector.getSearchType());
    }
    return allSearchTypes;      
  }
  
  /**
  * Set "enabledSearchTypes" global variable
  *
  * @param searchTypes List of search types in the form of a comma-separated string
  *
  * @return Success if the caller's role is administrator, Failure otherwise
  *
  * @anchor UnifiedSearch.PublicRestAPIs.UnifiedSearchService.enabled-searchtypes
  */
  @POST
  @Path("/enabled-searchtypes")
  public Response REST_setEnabledSearchtypes(@FormParam("searchTypes") String searchTypes) {
    Collection<String> roles = ConversationState.getCurrent().getIdentity().getRoles();    
    if(!roles.isEmpty() && roles.contains("administrators")) {//only administrators can set this
      settingService.set(Context.GLOBAL, Scope.APPLICATION, "enabledSearchTypes", new SettingValue<String>(searchTypes));      
      return Response.ok("ok", MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
    }
    return Response.ok("nok: administrators only", MediaType.APPLICATION_JSON).cacheControl(cacheControl).build();
  } 

  
}

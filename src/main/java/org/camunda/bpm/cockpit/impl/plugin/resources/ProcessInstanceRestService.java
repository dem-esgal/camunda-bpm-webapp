/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.cockpit.impl.plugin.resources;

import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Permissions.READ_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_INSTANCE;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.cockpit.impl.plugin.base.dto.ProcessInstanceDto;
import org.camunda.bpm.cockpit.impl.plugin.base.dto.query.ProcessInstanceQueryDto;
import org.camunda.bpm.cockpit.impl.plugin.base.sub.resources.ProcessInstanceResource;
import org.camunda.bpm.cockpit.plugin.resource.AbstractPluginResource;
import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.rest.dto.CountResultDto;
import org.camunda.bpm.engine.rest.mapper.MultipartFormData;

import org.camunda.bpm.webapp.utils.DeploymentDto;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

public class ProcessInstanceRestService extends AbstractPluginResource {

  public static final String PATH = "/process-instance";
  protected ObjectMapper objectMapper;
  private static final String WEBAPP_DIR;

  static {
    InputStream is = null;
    Properties props = null;
    try {
      props = new Properties();
      is = ProcessInstanceRestService.class.getClassLoader().getResourceAsStream("webapp.properties");
      props.load(is);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    WEBAPP_DIR = props.getProperty("tomcat.dir") ;

  }
  public ProcessInstanceRestService(String engineName) {
    super(engineName);
  }

  @Path("/{id}")
  public ProcessInstanceResource getProcessInstance(@PathParam("id") String id) {
    return new ProcessInstanceResource(getProcessEngine().getName(), id);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessInstanceDto> getProcessInstances(@Context UriInfo uriInfo,
      @QueryParam("firstResult") Integer firstResult, @QueryParam("maxResults") Integer maxResults) {
    ProcessInstanceQueryDto queryParameter = new ProcessInstanceQueryDto(uriInfo.getQueryParameters());
    return queryProcessInstances(queryParameter, firstResult, maxResults);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ProcessInstanceDto> queryProcessInstances(final ProcessInstanceQueryDto queryParameter,
      final @QueryParam("firstResult") Integer firstResult, final @QueryParam("maxResults") Integer maxResults) {

    return getCommandExecutor().executeCommand(new Command<List<ProcessInstanceDto>>() {
      public List<ProcessInstanceDto> execute(CommandContext commandContext) {
        injectObjectMapper(queryParameter);
        injectEngineConfig(queryParameter);
        paginate(queryParameter, firstResult, maxResults);
        configureExecutionQuery(queryParameter);
        return getQueryService().executeQuery("selectRunningProcessInstancesIncludingIncidents", queryParameter);
      }
    });

  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/count")
  public CountResultDto getProcessInstancesCount(@Context UriInfo uriInfo) {
    ProcessInstanceQueryDto queryParameter = new ProcessInstanceQueryDto(uriInfo.getQueryParameters());
    return queryProcessInstancesCount(queryParameter);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/count")
  public CountResultDto queryProcessInstancesCount(final ProcessInstanceQueryDto queryParameter) {

    return getCommandExecutor().executeCommand(new Command<CountResultDto>() {
      public CountResultDto execute(CommandContext commandContext) {
        injectEngineConfig(queryParameter);
        configureExecutionQuery(queryParameter);
        long result = getQueryService().executeQueryRowCount("selectRunningProcessInstancesCount", queryParameter);
        return new CountResultDto(result);
      }
    });

  }

  private String getFileName(MultivaluedMap<String, String> header) {

    String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

    for (String filename : contentDisposition) {
      if ((filename.trim().startsWith("filename"))) {

        String[] name = filename.split("=");

        String finalFileName = name[1].trim().replaceAll("\"", "");
        return finalFileName;
      }
    }
    return "unknown";
  }

  @POST
  @Path("/deploy-war")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public DeploymentDto deployWar(MultipartFormDataInput input)  {

    DeploymentDto deploymentDto = new DeploymentDto();// = DeploymentWithDefinitionsDto.fromDeployment(deployment);
    Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
    List<InputPart> inputParts = uploadForm.get("*");

    for (InputPart inputPart : inputParts) {
        FileOutputStream fos = null;
        try {
          fos = new FileOutputStream(WEBAPP_DIR + getFileName( inputPart.getHeaders()));
          fos.write(IOUtils.toByteArray(inputPart.getBody(InputStream.class,null)));
        } catch (java.io.IOException e) {
          e.printStackTrace();
        } finally {
          if (fos != null) {
            try {
              fos.close();
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
      }
    }
  /* URI uri = uriInfo.getBaseUriBuilder()
                     .path("/")
                     .path(DeploymentRestService.PATH)
                     .path(deploymentDto.getId())
                     .build();

    // GET
    deploymentDto.addReflexiveLink(uri, HttpMethod.GET, "self");
*/
    return deploymentDto;

    /*} else {
      throw new InvalidRequestException(Response.Status.BAD_REQUEST, "No deployment resources contained in the form upload.");
    }*/
  }
  private void paginate(ProcessInstanceQueryDto queryParameter, Integer firstResult, Integer maxResults) {
    if (firstResult == null) {
      firstResult = 0;
    }
    if (maxResults == null) {
      maxResults = Integer.MAX_VALUE;
    }
    queryParameter.setFirstResult(firstResult);
    queryParameter.setMaxResults(maxResults);
  }

  private void injectEngineConfig(ProcessInstanceQueryDto parameter) {

    ProcessEngineConfigurationImpl processEngineConfiguration = ((ProcessEngineImpl) getProcessEngine()).getProcessEngineConfiguration();
    if (processEngineConfiguration.getHistoryLevel().equals(HistoryLevel.HISTORY_LEVEL_NONE)) {
      parameter.setHistoryEnabled(false);
    }

    parameter.initQueryVariableValues(processEngineConfiguration.getVariableSerializers());
  }

  protected void configureExecutionQuery(ProcessInstanceQueryDto query) {
    configureAuthorizationCheck(query);
    configureTenantCheck(query);
    addPermissionCheck(query, PROCESS_INSTANCE, "RES.PROC_INST_ID_", READ);
    addPermissionCheck(query, PROCESS_DEFINITION, "P.KEY_", READ_INSTANCE);
  }

  protected void injectObjectMapper(ProcessInstanceQueryDto queryParameter) {
    queryParameter.setObjectMapper(objectMapper);
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

}

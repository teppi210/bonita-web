package org.bonitasoft.console.common.server.form;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.bonitasoft.console.common.server.page.PageRenderer;
import org.bonitasoft.engine.bpm.flownode.ActivityInstanceNotFoundException;
import org.bonitasoft.engine.bpm.process.ArchivedProcessInstanceNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;
import org.bonitasoft.engine.exception.FormMappingNotFoundException;
import org.bonitasoft.engine.page.PageNotFoundException;
import org.bonitasoft.engine.session.APISession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProcessFormServletTest {

    @Mock
    PageRenderer pageRenderer;

    @Mock
    ProcessFormService processFormService;

    @Spy
    @InjectMocks
    ProcessFormServlet formServlet;

    @Mock(answer = Answers.RETURNS_MOCKS)
    HttpServletRequest hsRequest;

    @Mock
    HttpServletResponse hsResponse;

    @Mock
    HttpSession httpSession;

    @Mock
    APISession apiSession;

    @Before
    public void beforeEach() throws Exception {
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getSession()).thenReturn(httpSession);
        when(httpSession.getAttribute("apiSession")).thenReturn(apiSession);
        when(apiSession.getUserId()).thenReturn(1L);
    }

    @Test
    public void should_get_Forbidden_Status_when_unauthorized() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(hsRequest.getParameter("user")).thenReturn(null);
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(new FormReference("custompage_form", false));
        when(formServlet.isAuthorized(any(APISession.class), anyLong(), anyLong(), anyLong(), anyLong(), anyBoolean())).thenReturn(false);

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).sendError(403, "User not Authorized");
    }

    @Test
    public void should_get_Bad_Request_when_invalid_parameters() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("");
        when(hsRequest.getParameter(anyString())).thenReturn(null);
        when(processFormService.getProcessDefinitionId(apiSession, null, null)).thenReturn(-1L);
        formServlet.doGet(hsRequest, hsResponse);
        verify(hsResponse, times(1)).sendError(400,
                "Either process name and version are required or process instance Id (with or without task name) or task instance Id.");
    }

    @Test
    public void should_display_externalPage() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        when(processFormService.getForm(apiSession, 1L, null, false)).thenReturn(new FormReference("/externalPage", true));

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayExternalPage(hsRequest, hsResponse, 1L, -1L, -1L, "/externalPage");
        verify(hsResponse, times(1)).encodeRedirectURL("/externalPage?process=1");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void should_display_customPage() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(new FormReference("custompage_form", false));

        formServlet.doGet(hsRequest, hsResponse);

        verify(pageRenderer, times(1)).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");
    }

    @Test
    public void should_display_legacyForm_when_no_mapping() throws Exception {
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        doThrow(FormMappingNotFoundException.class).when(processFormService).getForm(any(APISession.class), anyLong(), anyString(), anyBoolean());
        when(processFormService.getProcessDefinitionUUID(apiSession, 1L)).thenReturn("processName--processVersion");
        when(pageRenderer.getCurrentLocale(hsRequest)).thenReturn(new Locale("en"));

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayLegacyForm(hsRequest, hsResponse, apiSession, 1L, -1L, -1L, null, -1L);
        verify(hsResponse, times(1)).encodeRedirectURL(
                "/bonita/portal/homepage?ui=form&locale=en&theme=1#mode=form&form=processName--processVersion%24entry&process=1&autoInstantiate=false");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void should_display_legacyForm_for_process_when_mapping_on_legacy() throws Exception {
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(
                new FormReference(ProcessFormService.LEGACY_FORMS_NAME, false));
        when(processFormService.getProcessDefinitionUUID(apiSession, 1L)).thenReturn("processName--processVersion");
        when(pageRenderer.getCurrentLocale(hsRequest)).thenReturn(new Locale("en"));

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayLegacyForm(hsRequest, hsResponse, apiSession, 1L, -1L, -1L, null, -1L);
        verify(hsResponse, times(1)).encodeRedirectURL(
                "/bonita/portal/homepage?ui=form&locale=en&theme=1#mode=form&form=processName--processVersion%24entry&process=1&autoInstantiate=false");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void should_display_legacyForm_for_instance_when_mapping_on_legacy() throws Exception {
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, 42L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToSeeProcessInstance(apiSession, 1L, 42L, 1L)).thenReturn(true);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(
                new FormReference(ProcessFormService.LEGACY_FORMS_NAME, false));
        when(processFormService.getProcessDefinitionUUID(apiSession, 1L)).thenReturn("processName--processVersion");
        when(pageRenderer.getCurrentLocale(hsRequest)).thenReturn(new Locale("en"));

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayLegacyForm(hsRequest, hsResponse, apiSession, 1L, 42L, -1L, null, -1L);
        verify(hsResponse, times(1)).encodeRedirectURL(
                "/bonita/portal/homepage?ui=form&locale=en&theme=1#mode=form&form=processName--processVersion%24recap&instance=42&recap=true");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void should_display_legacyForm_for_task_when_mapping_on_legacy() throws Exception {
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getPathInfo()).thenReturn("/taskInstance/42");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, -1L, 42L)).thenReturn(1L);
        when(processFormService.getTaskName(apiSession, 42L)).thenReturn("taskName");
        when(processFormService.isAllowedToSeeTask(apiSession, 1L, 42L, 1L, false)).thenReturn(true);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(
                new FormReference(ProcessFormService.LEGACY_FORMS_NAME, false));
        when(processFormService.getProcessDefinitionUUID(apiSession, 1L)).thenReturn("processName--processVersion");
        when(pageRenderer.getCurrentLocale(hsRequest)).thenReturn(new Locale("en"));

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayLegacyForm(hsRequest, hsResponse, apiSession, 1L, -1L, 42L, "taskName", -1L);
        verify(hsResponse, times(1)).encodeRedirectURL(
                "/bonita/portal/homepage?ui=form&locale=en&theme=1#mode=form&form=processName--processVersion--taskName%24entry&task=42");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void should_display_customPage_resource_for_process() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion/path/of/resource.css");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        final FormReference form = new FormReference("custompage_form", false);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(form);

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayForm(hsRequest, hsResponse, apiSession, 1L, -1L, -1L, form, "path/of/resource.css");
    }

    @Test
    public void should_display_customPage_resource_for_instance() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42/path/of/resource.css");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, 42L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToSeeProcessInstance(apiSession, 1L, 42L, 1L)).thenReturn(true);
        final FormReference form = new FormReference("custompage_form", false);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(form);

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).displayForm(hsRequest, hsResponse, apiSession, 1L, 42L, -1L, form, "path/of/resource.css");
    }

    @Test
    public void should_get_not_found_if_the_page_does_not_exist() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        when(processFormService.getForm(any(APISession.class), anyLong(), anyString(), anyBoolean())).thenReturn(new FormReference("custompage_form", false));
        doThrow(PageNotFoundException.class).when(pageRenderer).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).sendError(404, "Cannot find the form with name custompage_form");
    }

    @Test
    public void should_display_customPage_for_process() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        when(processFormService.getForm(apiSession, 1L, null, false)).thenReturn(new FormReference("custompage_form", false));

        formServlet.doGet(hsRequest, hsResponse);

        verify(pageRenderer, times(1)).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");
    }

    @Test
    public void should_display_customPage_for_instance() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, 42L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToSeeProcessInstance(apiSession, 1L, 42L, 1L)).thenReturn(true);
        when(processFormService.getForm(apiSession, 1L, null, true)).thenReturn(new FormReference("custompage_form", false));

        formServlet.doGet(hsRequest, hsResponse);

        verify(pageRenderer, times(1)).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");
    }

    @Test
    public void should_display_customPage_for_task() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/taskInstance/42");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, -1L, 42L)).thenReturn(1L);
        when(processFormService.isAllowedToSeeTask(apiSession, 1L, 42L, 1L, false)).thenReturn(true);
        when(processFormService.getForm(apiSession, 1L, null, false)).thenReturn(new FormReference("custompage_form", false));

        formServlet.doGet(hsRequest, hsResponse);

        verify(pageRenderer, times(1)).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");
    }

    @Test
    public void should_display_customPage_for_process_with_unicode_characters() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processus+%C3%A9%2B%C3%B8/%C3%B8");
        when(processFormService.getProcessDefinitionId(apiSession, "processus é+ø", "ø")).thenReturn(1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, 1L, -1L, -1L)).thenReturn(1L);
        when(processFormService.isAllowedToStartProcess(apiSession, 1L, 1L)).thenReturn(true);
        when(processFormService.getForm(apiSession, 1L, null, false)).thenReturn(new FormReference("custompage_form", false));

        formServlet.doGet(hsRequest, hsResponse);

        verify(pageRenderer, times(1)).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");
    }

    @Test
    public void should_redirect_for_task_from_instance() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42/task/taskName");
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getServletPath()).thenReturn("/portal/form");
        when(processFormService.getTaskInstanceId(apiSession, 42L, "taskName", -1L)).thenReturn(1L);

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).encodeRedirectURL("/bonita/portal/form/taskInstance/1");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void redirect_for_task_from_instance_with_unicode_characters() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42/task/task+%C3%A9%2B%C3%B8");
        when(hsRequest.getContextPath()).thenReturn("/bonita");
        when(hsRequest.getServletPath()).thenReturn("/portal/form");
        when(processFormService.getTaskInstanceId(apiSession, 42L, "task é+ø", -1L)).thenReturn(1L);

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).encodeRedirectURL("/bonita/portal/form/taskInstance/1");
        verify(hsResponse, times(1)).sendRedirect(anyString());
    }

    @Test
    public void should_get_not_found_when_invalid_process() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/process/processName/processVersion");
        when(processFormService.getProcessDefinitionId(apiSession, "processName", "processVersion")).thenThrow(ProcessDefinitionNotFoundException.class);

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).sendError(404, "Cannot find the process");
    }

    @Test
    public void should_get_not_found_when_invalid_processInstanceId() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, 42L, -1L)).thenThrow(ArchivedProcessInstanceNotFoundException.class);

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).sendError(404, "Cannot find the process instance");
    }

    @Test
    public void should_get_not_found_when_invalid_task() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/processInstance/42/task/taskName");
        when(processFormService.getTaskInstanceId(apiSession, 42L, "taskName", -1L)).thenReturn(-1L);
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, 42L, -1L)).thenThrow(ActivityInstanceNotFoundException.class);

        formServlet.doGet(hsRequest, hsResponse);

        verify(hsResponse, times(1)).sendError(404, "Cannot find the task instance");
    }

    @Test
    public void should_get_server_error_when_issue_with_customPage() throws Exception {
        when(hsRequest.getPathInfo()).thenReturn("/taskInstance/42");
        when(processFormService.ensureProcessDefinitionId(apiSession, -1L, -1L, 42L)).thenReturn(1L);
        when(processFormService.getTaskName(apiSession, 42L)).thenReturn("taskName");
        when(processFormService.isAllowedToSeeTask(apiSession, 1L, 42L, 1L, false)).thenReturn(true);
        when(processFormService.getForm(apiSession, 1L, "taskName", false)).thenReturn(new FormReference("custompage_form", false));
        final InstantiationException instantiationException = new InstantiationException("instatiation exception");
        doThrow(instantiationException).when(pageRenderer).displayCustomPage(hsRequest, hsResponse, apiSession, "custompage_form");

        formServlet.doGet(hsRequest, hsResponse);

        verify(formServlet, times(1)).handleException(hsResponse, 1L, "taskName", false, instantiationException);
        verify(hsResponse, times(1)).sendError(500, "instatiation exception");
    }
}
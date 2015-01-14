package co.cask.cdap.internal.app.runtime.schedule;

import co.cask.cdap.AppWithWorkflow;
import co.cask.cdap.api.schedule.Schedule;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.proto.Id;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.test.internal.AppFabricTestHelper;
import com.google.common.collect.ImmutableList;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class SchedulerServiceTest {
  public static SchedulerService schedulerService;

  private static final Id.Account account = new Id.Account(Constants.DEFAULT_NAMESPACE);
  private static final Id.Application appId = new Id.Application(account, AppWithWorkflow.class.getSimpleName());
  private static final Id.Program program = new Id.Program(appId, AppWithWorkflow.SampleWorkflow.class.getSimpleName());
  private static final ProgramType programType = ProgramType.WORKFLOW;
  private static final Schedule schedule1 = new Schedule("Schedule1", "Every minute", "* * * * ?", Schedule.Action.START);
  private static final Schedule schedule2 = new Schedule("Schedule2", "Every Hour", "0 * * * ?", Schedule.Action.START);

  @BeforeClass
  public static void set() {
    schedulerService = AppFabricTestHelper.getInjector().getInstance(SchedulerService.class);
  }

  @AfterClass
  public static void finish() {
    schedulerService.stopAndWait();
  }

  @Test
  public void testSchedulesAcrossNamespace() throws Exception {
    AppFabricTestHelper.deployApplication(AppWithWorkflow.class);
    schedulerService.schedule(program, programType, ImmutableList.of(schedule1));

    Id.Program programInOtherNamespace =
      Id.Program.from(new Id.Application(new Id.Account("otherNamespace"), appId.getId()), program.getId());

    List<String> scheduleIds = schedulerService.getScheduleIds(program, programType);
    Assert.assertEquals(1, scheduleIds.size());

    List<String> scheduleIdsOtherNamespace = schedulerService.getScheduleIds(programInOtherNamespace, programType);
    Assert.assertEquals(0, scheduleIdsOtherNamespace.size());

    schedulerService.schedule(programInOtherNamespace, programType, ImmutableList.of(schedule2));

    scheduleIdsOtherNamespace = schedulerService.getScheduleIds(programInOtherNamespace, programType);
    Assert.assertEquals(1, scheduleIdsOtherNamespace.size());

    Assert.assertNotEquals(scheduleIds.get(0), scheduleIdsOtherNamespace.get(0));

  }

  @Test
  public void testSimpleSchedulerLifecycle() throws Exception {
    AppFabricTestHelper.deployApplication(AppWithWorkflow.class);

    schedulerService.schedule(program, programType, ImmutableList.of(schedule1));
    List<String> scheduleIds = schedulerService.getScheduleIds(program, programType);
    Assert.assertEquals(1, scheduleIds.size());
    checkState(Scheduler.ScheduleState.SCHEDULED, scheduleIds);

    schedulerService.schedule(program, programType, ImmutableList.of(schedule2));
    scheduleIds = schedulerService.getScheduleIds(program, programType);
    Assert.assertEquals(2, scheduleIds.size());

    checkState(Scheduler.ScheduleState.SCHEDULED, scheduleIds);

    for (String scheduleId : scheduleIds) {
      schedulerService.suspendSchedule(scheduleId);
    }
    checkState(Scheduler.ScheduleState.SUSPENDED, scheduleIds);

    schedulerService.deleteSchedules(program, programType);
    Assert.assertEquals(0, schedulerService.getScheduleIds(program, programType).size());
    checkState(Scheduler.ScheduleState.NOT_FOUND, scheduleIds);
  }

  private void checkState(Scheduler.ScheduleState expectedState, List<String> scheduleIds) {
    for (String scheduleId : scheduleIds) {
      Assert.assertEquals(expectedState, schedulerService.scheduleState(scheduleId));
    }
  }
}
<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoCheckbox, WegoInput, WegoPageHeader, WegoPanel, WegoSelect } from "@wego/ui";
import { type AuthSession, clearAuthSession, hasPermission, readAuthSession } from "../composables/useAuthSession";
import {
  advanceCourseEnrollment,
  assignCourseInstructor,
  type CourseEnrollment,
  type Diver,
  DiversApiError,
  type EnrollmentStage,
  enrollDiverInCourse,
  listCourseEnrollments,
  listDivers,
  listOfferings,
  listSkillEvaluations,
  type Offering,
  recordSkillEvaluation,
  type SkillEvaluation,
  withdrawCourseEnrollment,
} from "../composables/useDiversApi";

definePageMeta({ layout: "app-shell" });

useHead({ title: "Course Enrollments · Wego Platform" });

const session = ref<AuthSession | null>(null);
const enrollments = ref<CourseEnrollment[]>([]);
const listState = ref<"idle" | "loading" | "loaded" | "error">("idle");
const listError = ref("");
const stageFilter = ref<EnrollmentStage | "">("");

// Real names for the ids on each enrollment row — fetched once, not per row.
const divers = ref<Diver[]>([]);
const courseOfferings = ref<Offering[]>([]);

const canManage = () => hasPermission(session.value, "course:manage");
const canView = () => hasPermission(session.value, "course:view");

function diverName(diverId: string): string {
  return divers.value.find((diver) => diver.id === diverId)?.fullName ?? diverId;
}

function offeringTitle(offeringId: string): string {
  return courseOfferings.value.find((offering) => offering.id === offeringId)?.title ?? offeringId;
}

function stageTone(stage: EnrollmentStage): "success" | "accent" | "neutral" {
  if (stage === "CERTIFIED") return "success";
  if (stage === "WITHDRAWN") return "neutral";
  return "accent";
}

function handleApiError(error: unknown) {
  if (error instanceof DiversApiError && error.status === 401) {
    clearAuthSession();
    session.value = null;
  }
}

function errorText(error: unknown): string {
  if (error instanceof DiversApiError) {
    if (error.status === 401) return "Your session has expired. Please sign in again.";
    if (error.status === 403) return "You don't have permission for this.";
    if (error.errorCode === "diver_not_found") return "No diver with that id.";
    if (error.errorCode === "offering_not_found") return "No offering with that id.";
    if (error.errorCode === "offering_is_not_a_course") return "That offering isn't a course.";
    if (error.errorCode === "enrollment_finished") return "This enrollment has already finished.";
    if (error.status === 404) return "Not found.";
    return `Request failed (${error.errorCode}).`;
  }
  return "Could not reach the server. Check your connection and try again.";
}

async function loadEnrollments() {
  if (!session.value || !canView()) {
    enrollments.value = [];
    listState.value = "loaded";
    return;
  }
  listState.value = "loading";
  listError.value = "";
  try {
    const [enrollmentResult, diverResult, offeringResult] = await Promise.all([
      listCourseEnrollments(session.value.token, { stage: stageFilter.value || undefined }),
      divers.value.length === 0 ? listDivers(session.value.token, { status: "ACTIVE" }) : Promise.resolve(divers.value),
      courseOfferings.value.length === 0 ? listOfferings(session.value.token, { type: "COURSE" }) : Promise.resolve(courseOfferings.value),
    ]);
    enrollments.value = enrollmentResult;
    divers.value = diverResult;
    courseOfferings.value = offeringResult;
    listState.value = "loaded";
  } catch (error) {
    handleApiError(error);
    listState.value = "error";
    listError.value = errorText(error);
  }
}

function runFilter() {
  loadEnrollments();
}

const enrollForm = ref({ diverId: "", offeringId: "" });
const enrollState = ref<"idle" | "submitting" | "error">("idle");
const enrollError = ref("");

async function submitEnroll() {
  if (!session.value || !enrollForm.value.diverId || !enrollForm.value.offeringId) return;
  enrollState.value = "submitting";
  enrollError.value = "";
  try {
    const created = await enrollDiverInCourse(session.value.token, enrollForm.value.diverId, enrollForm.value.offeringId);
    enrollments.value = [created, ...enrollments.value];
    enrollForm.value = { diverId: "", offeringId: "" };
    enrollState.value = "idle";
  } catch (error) {
    handleApiError(error);
    enrollState.value = "error";
    enrollError.value = errorText(error);
  }
}

const actionState = ref<Record<string, "idle" | "submitting" | "error">>({});
const actionError = ref<Record<string, string>>({});
const instructorInput = ref<Record<string, string>>({});

async function submitAssignInstructor(enrollment: CourseEnrollment) {
  if (!session.value) return;
  const instructorUserId = instructorInput.value[enrollment.id];
  if (!instructorUserId) return;

  actionState.value[enrollment.id] = "submitting";
  actionError.value[enrollment.id] = "";
  try {
    const updated = await assignCourseInstructor(session.value.token, enrollment.id, instructorUserId);
    enrollments.value = enrollments.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[enrollment.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[enrollment.id] = "error";
    actionError.value[enrollment.id] = errorText(error);
  }
}

async function submitAdvance(enrollment: CourseEnrollment) {
  if (!session.value) return;
  actionState.value[enrollment.id] = "submitting";
  actionError.value[enrollment.id] = "";
  try {
    const updated = await advanceCourseEnrollment(session.value.token, enrollment.id);
    enrollments.value = enrollments.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[enrollment.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[enrollment.id] = "error";
    actionError.value[enrollment.id] = errorText(error);
  }
}

async function submitWithdraw(enrollment: CourseEnrollment) {
  if (!session.value) return;
  if (!window.confirm(`Withdraw ${diverName(enrollment.diverId)} from this course? This cannot be undone.`)) return;

  actionState.value[enrollment.id] = "submitting";
  actionError.value[enrollment.id] = "";
  try {
    const updated = await withdrawCourseEnrollment(session.value.token, enrollment.id);
    enrollments.value = enrollments.value.map((existing) => (existing.id === updated.id ? updated : existing));
    actionState.value[enrollment.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[enrollment.id] = "error";
    actionError.value[enrollment.id] = errorText(error);
  }
}

const expandedEnrollmentId = ref<string | null>(null);
const skillEvaluations = ref<Record<string, SkillEvaluation[]>>({});
const detailState = ref<Record<string, "idle" | "loading" | "error">>({});
const newSkill = ref({ skillName: "", passed: true, evaluatedOn: "", notes: "" });

async function toggleDetails(enrollment: CourseEnrollment) {
  if (expandedEnrollmentId.value === enrollment.id) {
    expandedEnrollmentId.value = null;
    return;
  }
  expandedEnrollmentId.value = enrollment.id;
  newSkill.value = { skillName: "", passed: true, evaluatedOn: "", notes: "" };
  if (!session.value) return;

  detailState.value[enrollment.id] = "loading";
  try {
    skillEvaluations.value[enrollment.id] = await listSkillEvaluations(session.value.token, enrollment.id);
    detailState.value[enrollment.id] = "idle";
  } catch (error) {
    handleApiError(error);
    detailState.value[enrollment.id] = "error";
  }
}

async function submitSkillEvaluation(enrollment: CourseEnrollment) {
  if (!session.value || !newSkill.value.skillName || !newSkill.value.evaluatedOn) return;
  actionState.value[enrollment.id] = "submitting";
  actionError.value[enrollment.id] = "";
  try {
    const recorded = await recordSkillEvaluation(session.value.token, enrollment.id, {
      skillName: newSkill.value.skillName,
      passed: newSkill.value.passed,
      evaluatedOn: newSkill.value.evaluatedOn,
      notes: newSkill.value.notes || undefined,
    });
    skillEvaluations.value[enrollment.id] = [recorded, ...(skillEvaluations.value[enrollment.id] ?? [])];
    newSkill.value = { skillName: "", passed: true, evaluatedOn: "", notes: "" };
    actionState.value[enrollment.id] = "idle";
  } catch (error) {
    handleApiError(error);
    actionState.value[enrollment.id] = "error";
    actionError.value[enrollment.id] = errorText(error);
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (session.value) loadEnrollments();
});
</script>

<template>
  <WegoPageHeader
    title="Course Enrollments"
    description="Lead → Theory → Pool → Open Water → Certified. Real progress for a real diver in a real course."
  />

  <div v-if="!session" class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <p>You need to sign in to view course enrollments.</p>
    <NuxtLink to="/login" class="mt-3 inline-block text-wego-accent underline">Sign in</NuxtLink>
  </div>

  <template v-else>
    <WegoAlert v-if="listState === 'error'" variant="danger" class="mt-6">{{ listError }}</WegoAlert>

    <WegoPanel title="Enrollments" class="mt-8">
      <p v-if="!canView()" class="text-sm text-wego-muted">
        Your account doesn't have permission to view course enrollments (course:view).
      </p>
      <template v-else>
        <div class="flex flex-wrap items-end gap-3">
          <WegoSelect id="stageFilter" v-model="stageFilter" label="Stage" @change="runFilter">
            <option value="">All</option>
            <option value="LEAD">Lead</option>
            <option value="THEORY">Theory</option>
            <option value="POOL">Pool</option>
            <option value="OPEN_WATER">Open Water</option>
            <option value="CERTIFIED">Certified</option>
            <option value="WITHDRAWN">Withdrawn</option>
          </WegoSelect>
          <WegoButton type="button" variant="secondary" @click="runFilter">Filter</WegoButton>
        </div>

        <p v-if="listState === 'loading'" class="mt-3 text-sm text-wego-muted">Loading…</p>
        <p v-else-if="listState === 'loaded' && enrollments.length === 0" class="mt-3 text-sm text-wego-muted">No enrollments yet.</p>
        <ul v-else class="mt-4 space-y-3">
          <li v-for="enrollment in enrollments" :key="enrollment.id" class="rounded-wego-control border border-wego-border p-4">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <p class="font-semibold">{{ diverName(enrollment.diverId) }}</p>
                  <WegoBadge :tone="stageTone(enrollment.stage)">{{ enrollment.stage }}</WegoBadge>
                </div>
                <p class="mt-1 text-sm text-wego-muted">
                  {{ offeringTitle(enrollment.offeringId) }}<span v-if="enrollment.instructorUserId"> · instructor assigned</span>
                </p>
              </div>
              <div v-if="canManage()" class="flex shrink-0 flex-wrap gap-2">
                <WegoButton type="button" variant="secondary" @click="toggleDetails(enrollment)">
                  {{ expandedEnrollmentId === enrollment.id ? "Hide details" : "Details" }}
                </WegoButton>
                <WegoButton
                  v-if="!['CERTIFIED', 'WITHDRAWN'].includes(enrollment.stage)"
                  type="button"
                  variant="secondary"
                  :disabled="actionState[enrollment.id] === 'submitting'"
                  @click="submitAdvance(enrollment)"
                >
                  Advance
                </WegoButton>
                <WegoButton
                  v-if="!['CERTIFIED', 'WITHDRAWN'].includes(enrollment.stage)"
                  type="button"
                  variant="secondary"
                  :disabled="actionState[enrollment.id] === 'submitting'"
                  @click="submitWithdraw(enrollment)"
                >
                  Withdraw
                </WegoButton>
              </div>
            </div>

            <WegoAlert v-if="actionState[enrollment.id] === 'error'" variant="danger" class="mt-2">
              {{ actionError[enrollment.id] }}
            </WegoAlert>

            <div v-if="expandedEnrollmentId === enrollment.id" class="mt-4 grid gap-4 border-t border-wego-border pt-4 sm:grid-cols-2">
              <div v-if="canManage() && !['CERTIFIED', 'WITHDRAWN'].includes(enrollment.stage)">
                <h3 class="text-sm font-semibold">Instructor</h3>
                <div class="mt-2 flex flex-wrap items-end gap-2">
                  <WegoInput
                    :id="`instructor-${enrollment.id}`"
                    :model-value="instructorInput[enrollment.id] ?? ''"
                    label="Instructor user id"
                    class="min-w-0 flex-1"
                    @update:model-value="(value) => (instructorInput[enrollment.id] = value)"
                  />
                  <WegoButton
                    type="button"
                    variant="secondary"
                    :disabled="actionState[enrollment.id] === 'submitting'"
                    @click="submitAssignInstructor(enrollment)"
                  >
                    Assign
                  </WegoButton>
                </div>
              </div>

              <div>
                <h3 class="text-sm font-semibold">Skill evaluations</h3>
                <p v-if="detailState[enrollment.id] === 'loading'" class="mt-2 text-xs text-wego-muted">Loading…</p>
                <ul class="mt-2 space-y-2">
                  <li v-for="evaluation in skillEvaluations[enrollment.id] ?? []" :key="evaluation.id" class="text-xs text-wego-muted">
                    {{ evaluation.evaluatedOn }} — {{ evaluation.skillName }} ({{ evaluation.passed ? "passed" : "not yet" }})
                  </li>
                  <li v-if="detailState[enrollment.id] === 'idle' && (skillEvaluations[enrollment.id] ?? []).length === 0" class="text-xs text-wego-muted">
                    No evaluations logged yet.
                  </li>
                </ul>
                <form v-if="canManage()" class="mt-3 space-y-2" @submit.prevent="submitSkillEvaluation(enrollment)">
                  <WegoInput :id="`skill-name-${enrollment.id}`" v-model="newSkill.skillName" label="Skill" required />
                  <WegoInput :id="`skill-date-${enrollment.id}`" v-model="newSkill.evaluatedOn" label="Evaluated on" type="date" required />
                  <WegoCheckbox :id="`skill-passed-${enrollment.id}`" v-model="newSkill.passed">Passed</WegoCheckbox>
                  <WegoButton type="submit" variant="secondary" :disabled="actionState[enrollment.id] === 'submitting'">
                    Log evaluation
                  </WegoButton>
                </form>
              </div>
            </div>
          </li>
        </ul>
      </template>
    </WegoPanel>

    <WegoPanel v-if="canManage()" title="Enroll a diver" class="mt-8">
      <form class="space-y-5" @submit.prevent="submitEnroll">
        <WegoSelect id="enrollDiver" v-model="enrollForm.diverId" label="Diver">
          <option value="" disabled>Select a diver…</option>
          <option v-for="diver in divers" :key="diver.id" :value="diver.id">{{ diver.fullName }}</option>
        </WegoSelect>
        <WegoSelect id="enrollOffering" v-model="enrollForm.offeringId" label="Course">
          <option value="" disabled>Select a course…</option>
          <option v-for="offering in courseOfferings" :key="offering.id" :value="offering.id">{{ offering.title }}</option>
        </WegoSelect>

        <WegoAlert v-if="enrollState === 'error'" variant="danger">{{ enrollError }}</WegoAlert>

        <WegoButton type="submit" :disabled="enrollState === 'submitting'" :loading="enrollState === 'submitting'">
          Enroll
        </WegoButton>
      </form>
    </WegoPanel>
  </template>
</template>

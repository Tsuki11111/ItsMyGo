<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchProductReviews, fetchReviewSummary } from '@/api/review'
import {
  createProductQuestion,
  createQuestionAnswer,
  fetchProductQuestions,
  fetchQuestionAnswers,
} from '@/api/qa'
import { useUserStore } from '@/stores/user'
import type {
  ProductAnswerView,
  ProductQuestionView,
  ReviewSummary,
  ReviewView,
} from '@/api/types'

const props = defineProps<{ productId: number }>()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const REVIEW_SIZE = 5
const QUESTION_SIZE = 5
const ANSWER_SIZE = 10

type Tab = 'reviews' | 'questions'
type QuestionFilter = 'all' | 'unanswered' | 'answered'

const activeTab = ref<Tab>('reviews')

/* ── 买家评价 ── */
const summary = ref<ReviewSummary | null>(null)
const reviews = ref<ReviewView[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewLoading = ref(false)
const reviewRating = ref<number | null>(null)
const ratingBars = computed(() => {
  const distribution = summary.value?.distribution ?? {}
  const total = summary.value?.count ?? 0
  return [5, 4, 3, 2, 1].map((star) => {
    const count = Number(distribution[String(star)] ?? 0)
    return { star, count, percent: total > 0 ? (count / total) * 100 : 0 }
  })
})

/* ── 问大家 ── */
const questions = ref<ProductQuestionView[]>([])
const questionTotal = ref(0)
const questionPage = ref(1)
const questionFilter = ref<QuestionFilter>('all')
const questionLoading = ref(false)
const askContent = ref('')
const asking = ref(false)

const answerDialogVisible = ref(false)
const activeQuestion = ref<ProductQuestionView | null>(null)
const answers = ref<ProductAnswerView[]>([])
const answerTotal = ref(0)
const answerPage = ref(1)
const answerLoading = ref(false)
const answerContent = ref('')
const answering = ref(false)

const questionFilters: { key: QuestionFilter; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'unanswered', label: '待回答' },
  { key: 'answered', label: '已回答' },
]

const ratingFilters = [5, 4, 3, 2, 1]

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function openLogin() {
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
}

async function loadSummary() {
  try {
    summary.value = await fetchReviewSummary(props.productId)
  } catch {
    summary.value = null
  }
}

async function loadReviews(reset = true) {
  if (reset) {
    reviewPage.value = 1
    reviews.value = []
  }
  reviewLoading.value = true
  try {
    const page = await fetchProductReviews(
      props.productId,
      reviewPage.value,
      REVIEW_SIZE,
      reviewRating.value ?? undefined,
    )
    reviews.value = reset ? page.records : [...reviews.value, ...page.records]
    reviewTotal.value = page.total
  } finally {
    reviewLoading.value = false
  }
}

function chooseRating(rating: number | null) {
  reviewRating.value = rating
  void loadReviews(true)
}

function loadMoreReviews() {
  reviewPage.value += 1
  void loadReviews(false)
}

async function loadQuestions(reset = true) {
  if (reset) {
    questionPage.value = 1
    questions.value = []
  }
  questionLoading.value = true
  try {
    const page = await fetchProductQuestions(
      props.productId,
      questionPage.value,
      QUESTION_SIZE,
      questionFilter.value,
    )
    questions.value = reset ? page.records : [...questions.value, ...page.records]
    questionTotal.value = page.total
  } finally {
    questionLoading.value = false
  }
}

function chooseQuestionFilter(filter: QuestionFilter) {
  questionFilter.value = filter
  void loadQuestions(true)
}

function loadMoreQuestions() {
  questionPage.value += 1
  void loadQuestions(false)
}

async function askQuestion() {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后就可以向买过的人提问')
    openLogin()
    return
  }
  const content = askContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  try {
    await createProductQuestion(props.productId, content)
    askContent.value = ''
    activeTab.value = 'questions'
    ElMessage.success('提问已发布')
    await loadQuestions(true)
  } finally {
    asking.value = false
  }
}

async function openAnswers(question: ProductQuestionView) {
  activeQuestion.value = question
  answerDialogVisible.value = true
  answers.value = []
  answerTotal.value = 0
  answerPage.value = 1
  answerContent.value = ''
  await loadAnswers(true)
}

async function loadAnswers(reset = true) {
  if (!activeQuestion.value) return
  if (reset) {
    answerPage.value = 1
    answers.value = []
  }
  answerLoading.value = true
  try {
    const page = await fetchQuestionAnswers(
      props.productId,
      activeQuestion.value.id,
      answerPage.value,
      ANSWER_SIZE,
    )
    answers.value = reset ? page.records : [...answers.value, ...page.records]
    answerTotal.value = page.total
  } finally {
    answerLoading.value = false
  }
}

function loadMoreAnswers() {
  answerPage.value += 1
  void loadAnswers(false)
}

async function submitAnswer() {
  if (!activeQuestion.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后才可以回答')
    return
  }
  const content = answerContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入回答')
    return
  }
  answering.value = true
  try {
    await createQuestionAnswer(props.productId, activeQuestion.value.id, content)
    answerContent.value = ''
    ElMessage.success('回答已发布')
    await loadAnswers(true)
    const current = activeQuestion.value
    await loadQuestions(true)
    if (current) {
      activeQuestion.value = questions.value.find((item) => item.id === current.id) ?? current
    }
  } finally {
    answering.value = false
  }
}

watch(
  () => props.productId,
  () => {
    activeTab.value = 'reviews'
    reviewRating.value = null
    questionFilter.value = 'all'
    askContent.value = ''
    void Promise.all([loadSummary(), loadReviews(true), loadQuestions(true)])
  },
)

onMounted(() => {
  void Promise.all([loadSummary(), loadReviews(true), loadQuestions(true)])
})
</script>

<template>
  <section class="community-block">
    <div class="community-tabs">
      <button
        type="button"
        class="community-tab"
        :class="{ active: activeTab === 'reviews' }"
        @click="activeTab = 'reviews'"
      >
        商品评价
        <span class="tab-count">{{ summary?.count ?? 0 }}</span>
      </button>
      <button
        type="button"
        class="community-tab"
        :class="{ active: activeTab === 'questions' }"
        @click="activeTab = 'questions'"
      >
        问大家
        <span class="tab-count">{{ questionTotal }}</span>
      </button>
    </div>

    <template v-if="activeTab === 'reviews'">
      <div class="review-summary-panel">
        <div class="score-side">
          <strong class="score-num">{{ (summary?.averageRating ?? 0).toFixed(1) }}</strong>
          <el-rate
            :model-value="summary?.averageRating ?? 0"
            disabled
            allow-half
            size="small"
          />
          <span class="score-label">
            {{ summary && summary.count > 0 ? `${summary.count} 条买家评价` : '暂无买家评价' }}
          </span>
        </div>
        <div class="distribution">
          <div v-for="bar in ratingBars" :key="bar.star" class="dist-row">
            <span class="dist-star">{{ bar.star }} 星</span>
            <div class="dist-track">
              <i :style="{ width: `${bar.percent}%` }"></i>
            </div>
            <span class="dist-count">{{ bar.count }}</span>
          </div>
        </div>
      </div>

      <div class="rating-filters">
        <button
          type="button"
          class="rating-filter"
          :class="{ active: reviewRating === null }"
          @click="chooseRating(null)"
        >
          全部
        </button>
        <button
          v-for="star in ratingFilters"
          :key="star"
          type="button"
          class="rating-filter"
          :class="{ active: reviewRating === star }"
          @click="chooseRating(star)"
        >
          {{ star }} 星
        </button>
      </div>

      <el-empty
        v-if="!reviewLoading && reviews.length === 0"
        :description="reviewRating === null ? '还没有评价' : '这个星级下还没有评价'"
        :image-size="60"
      />
      <div v-else v-loading="reviewLoading" class="review-list">
        <article v-for="review in reviews" :key="review.id" class="review-item">
          <div class="review-user">
            <div class="review-avatar">
              <img v-if="review.userAvatar" :src="review.userAvatar" alt="" />
              <span v-else>{{ review.userNickname.slice(0, 1) }}</span>
            </div>
            <span class="review-nickname">{{ review.userNickname }}</span>
            <span class="buyer-badge">已购买</span>
            <el-rate :model-value="review.rating" disabled size="small" />
            <span class="review-time">{{ formatTime(review.createdAt) }}</span>
          </div>
          <p v-if="review.content" class="review-content">{{ review.content }}</p>
          <p v-else class="review-content blank">（用户未填写文字评价）</p>
        </article>

        <el-button
          v-if="reviews.length < reviewTotal"
          link
          type="primary"
          :loading="reviewLoading"
          @click="loadMoreReviews"
        >
          查看更多评价
        </el-button>
      </div>
    </template>

    <template v-else>
      <div class="ask-box">
        <el-input
          v-model="askContent"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :placeholder="userStore.isLoggedIn
            ? '向买过的人提问，例如：这个型号支持壁挂吗？'
            : '登录后可以向买过的人提问'"
        />
        <div class="ask-foot">
          <span class="ask-hint">提问会公开展示，不参与商品评分</span>
          <el-button type="primary" :loading="asking" @click="askQuestion">
            {{ userStore.isLoggedIn ? '发布提问' : '登录后提问' }}
          </el-button>
        </div>
      </div>

      <div class="rating-filters">
        <button
          v-for="item in questionFilters"
          :key="item.key"
          type="button"
          class="rating-filter"
          :class="{ active: questionFilter === item.key }"
          @click="chooseQuestionFilter(item.key)"
        >
          {{ item.label }}
        </button>
      </div>

      <el-empty
        v-if="!questionLoading && questions.length === 0"
        description="还没有人提问，来问第一个问题吧"
        :image-size="60"
      />
      <div v-else v-loading="questionLoading" class="question-list">
        <article v-for="question in questions" :key="question.id" class="question-item">
          <div class="review-user">
            <div class="review-avatar">
              <img v-if="question.userAvatar" :src="question.userAvatar" alt="" />
              <span v-else>{{ question.userNickname.slice(0, 1) }}</span>
            </div>
            <span class="review-nickname">{{ question.userNickname }}</span>
            <span class="question-badge">商品提问</span>
            <span class="review-time">{{ formatTime(question.createdAt) }}</span>
          </div>
          <p class="question-content">{{ question.content }}</p>

          <div v-if="question.answers.length" class="answer-preview">
            <div v-for="answer in question.answers" :key="answer.id" class="answer-row">
              <span class="buyer-badge">已购买</span>
              <span class="answer-user">{{ answer.userNickname }}：</span>
              <span class="answer-content">{{ answer.content }}</span>
            </div>
          </div>
          <p v-else class="no-answer">还没有回答</p>

          <button type="button" class="answer-toggle" @click="openAnswers(question)">
            {{ question.answerCount > 0 ? `查看全部 ${question.answerCount} 条回答` : '回答问题' }}
          </button>
        </article>

        <el-button
          v-if="questions.length < questionTotal"
          link
          type="primary"
          :loading="questionLoading"
          @click="loadMoreQuestions"
        >
          查看更多提问
        </el-button>
      </div>
    </template>

    <el-dialog
      v-model="answerDialogVisible"
      title="问大家"
      width="min(620px, 94vw)"
      :close-on-click-modal="false"
    >
      <template v-if="activeQuestion">
        <div class="dialog-question">
          <span class="question-badge">商品提问</span>
          <span class="answer-user">{{ activeQuestion.userNickname }}：</span>
          {{ activeQuestion.content }}
        </div>

        <div v-loading="answerLoading" class="dialog-answers">
          <el-empty
            v-if="!answerLoading && answers.length === 0"
            description="还没有回答"
            :image-size="48"
          />
          <div v-for="answer in answers" :key="answer.id" class="dialog-answer">
            <div class="review-user">
              <div class="review-avatar small">
                <img v-if="answer.userAvatar" :src="answer.userAvatar" alt="" />
                <span v-else>{{ answer.userNickname.slice(0, 1) }}</span>
              </div>
              <span class="review-nickname">{{ answer.userNickname }}</span>
              <span class="buyer-badge">已购买</span>
              <span class="review-time">{{ formatTime(answer.createdAt) }}</span>
            </div>
            <p class="dialog-answer-content">{{ answer.content }}</p>
          </div>
          <el-button
            v-if="answers.length < answerTotal"
            link
            type="primary"
            :loading="answerLoading"
            @click="loadMoreAnswers"
          >
            查看更多回答
          </el-button>
        </div>

        <div class="answer-compose">
          <el-input
            v-model="answerContent"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="userStore.isLoggedIn
              ? '如果你买过这个商品，可以分享实际使用体验'
              : '登录后才可以回答'"
          />
          <div class="ask-foot">
            <span class="ask-hint">仅购买过该商品的用户可以回答</span>
            <el-button type="primary" :loading="answering" @click="submitAnswer">
              {{ userStore.isLoggedIn ? '发布回答' : '登录后回答' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.community-block {
  margin-bottom: 18px;
  padding: 20px 22px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.community-tabs {
  display: flex;
  gap: 26px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--c-border);
}

.community-tab {
  padding: 0 0 11px;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-family: inherit;
  font-size: 15px;
  cursor: pointer;
}

.community-tab.active {
  color: var(--c-pink);
  border-bottom-color: var(--c-pink);
  font-weight: 600;
}

.tab-count {
  margin-left: 4px;
  color: var(--c-text-3);
  font-size: 11px;
  font-weight: 400;
}

.review-summary-panel {
  display: flex;
  align-items: center;
  gap: 42px;
  padding: 4px 6px 18px;
}

.score-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 120px;
}

.score-num {
  color: var(--c-pink);
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  font-size: 34px;
  line-height: 1;
}

.score-label {
  color: var(--c-text-3);
  font-size: 11px;
}

.distribution {
  flex: 1;
  max-width: 440px;
}

.dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 5px 0;
  color: var(--c-text-3);
  font-size: 11px;
}

.dist-star {
  width: 30px;
  flex: none;
}

.dist-track {
  flex: 1;
  height: 6px;
  overflow: hidden;
  background: var(--c-pink-tint);
  border-radius: 4px;
}

.dist-track i {
  display: block;
  height: 100%;
  background: var(--c-pink);
  border-radius: 4px;
}

.dist-count {
  width: 28px;
  text-align: right;
}

.rating-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.rating-filter {
  padding: 4px 11px;
  color: var(--c-text-2);
  background: var(--c-card);
  border: 1px solid var(--c-border);
  border-radius: 999px;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}

.rating-filter.active {
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-color: var(--c-pink);
}

.review-list,
.question-list {
  margin-top: 4px;
}

.review-item,
.question-item {
  padding: 14px 0;
  border-top: 1px solid var(--c-border);
}

.review-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.review-avatar {
  width: 32px;
  height: 32px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-radius: 50%;
  font-size: 13px;
}

.review-avatar.small {
  width: 26px;
  height: 26px;
  font-size: 11px;
}

.review-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.review-nickname {
  color: var(--c-text);
  font-size: 13px;
}

.review-time {
  margin-left: auto;
  color: var(--c-text-3);
  font-size: 11px;
}

.buyer-badge,
.question-badge {
  flex: none;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
}

.buyer-badge {
  color: var(--c-pink);
  background: var(--c-pink-tint);
}

.question-badge {
  color: #7a5b00;
  background: #fff6dc;
}

.review-content,
.question-content {
  margin: 8px 0 0;
  color: var(--c-text-2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.review-content.blank {
  color: var(--c-text-3);
}

.question-content {
  color: var(--c-text);
  font-weight: 500;
}

.answer-preview {
  margin-top: 9px;
  padding: 8px 10px;
  background: #fafafa;
  border-radius: 6px;
}

.answer-row {
  margin: 3px 0;
  color: var(--c-text-2);
  font-size: 12px;
  line-height: 1.6;
}

.answer-user {
  color: var(--c-text);
  font-weight: 500;
}

.answer-content {
  white-space: pre-wrap;
}

.no-answer {
  margin: 8px 0 0;
  color: var(--c-text-3);
  font-size: 12px;
}

.answer-toggle {
  margin-top: 8px;
  padding: 0;
  color: var(--c-pink);
  background: none;
  border: none;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}

.answer-toggle:hover {
  text-decoration: underline;
}

.ask-box {
  padding: 12px;
  margin-bottom: 14px;
  background: #fafafa;
  border: 1px solid var(--c-border);
  border-radius: 8px;
}

.ask-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.ask-hint {
  color: var(--c-text-3);
  font-size: 11px;
}

.dialog-question {
  padding: 10px 12px;
  margin-bottom: 12px;
  color: var(--c-text);
  background: var(--c-pink-tint);
  border-radius: 7px;
  font-size: 13px;
  line-height: 1.7;
}

.dialog-answers {
  max-height: 38vh;
  margin-bottom: 14px;
  overflow: auto;
}

.dialog-answer {
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border);
}

.dialog-answer:last-child {
  border-bottom: none;
}

.dialog-answer-content {
  margin: 6px 0 0;
  color: var(--c-text-2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.answer-compose {
  padding-top: 12px;
  border-top: 1px solid var(--c-border);
}

@media (max-width: 640px) {
  .community-block {
    padding: 16px 14px;
  }

  .review-summary-panel {
    gap: 20px;
    align-items: flex-start;
  }

  .score-side {
    min-width: 80px;
  }

  .score-num {
    font-size: 28px;
  }

  .review-time {
    display: none;
  }
}
</style>

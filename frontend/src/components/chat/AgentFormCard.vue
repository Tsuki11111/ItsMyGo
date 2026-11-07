<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { AgentFormField, AgentFormRequest } from '@/api/types'

/**
 * 会话内表单卡片。
 *
 * - 流式 final 里的表单可交互；提交后由父组件带 formResult 发起下一轮，并置灰本轮。
 * - GetHistory 回放的表单只读（readonly），提示「仅展示」，不可再提交。
 */
const props = defineProps<{
  form: AgentFormRequest
  readonly?: boolean
  submitted?: boolean
}>()

const emit = defineEmits<{ submit: [values: Record<string, string>] }>()

const values = reactive<Record<string, string>>({})
const error = ref('')

const locked = computed(() => props.readonly === true || props.submitted === true)

function resetValues() {
  for (const key of Object.keys(values)) delete values[key]
  for (const field of props.form.fields) {
    values[field.name] = field.defaultValue ?? ''
  }
  error.value = ''
}

watch(() => props.form, resetValues, { immediate: true })

function onNumberChange(field: AgentFormField, value: number | null | undefined) {
  values[field.name] = value === null || value === undefined ? '' : String(value)
}

function submit() {
  if (locked.value) return
  const missing = props.form.fields.find(
    (field) => field.required && !String(values[field.name] ?? '').trim(),
  )
  if (missing) {
    error.value = `请填写「${missing.label || missing.name}」`
    return
  }
  error.value = ''
  emit('submit', { ...values })
}
</script>

<template>
  <section class="form-card" :class="{ locked }">
    <header class="form-head">
      <div class="form-title">
        <p class="form-name">{{ form.title || '请填写信息' }}</p>
        <p v-if="form.description" class="form-desc">{{ form.description }}</p>
      </div>
      <el-tag v-if="readonly" size="small" type="info">仅展示</el-tag>
      <el-tag v-else-if="submitted" size="small" type="success">已提交</el-tag>
    </header>

    <div class="form-fields">
      <div v-for="field in form.fields" :key="field.name" class="form-field">
        <label class="field-label">
          {{ field.label || field.name }}
          <span v-if="field.required" class="required">*</span>
        </label>

        <el-select
          v-if="field.type === 'select'"
          v-model="values[field.name]"
          :disabled="locked"
          :placeholder="field.placeholder || '请选择'"
          class="field-control"
        >
          <el-option
            v-for="option in field.options"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-select>

        <el-date-picker
          v-else-if="field.type === 'date'"
          v-model="values[field.name]"
          type="date"
          value-format="YYYY-MM-DD"
          :disabled="locked"
          :placeholder="field.placeholder || '请选择日期'"
          class="field-control"
        />

        <el-input-number
          v-else-if="field.type === 'number'"
          :model-value="values[field.name] === '' ? undefined : Number(values[field.name])"
          :disabled="locked"
          :placeholder="field.placeholder"
          controls-position="right"
          class="field-control"
          @update:model-value="onNumberChange(field, $event)"
        />

        <el-input
          v-else
          v-model="values[field.name]"
          :disabled="locked"
          :placeholder="field.placeholder"
          class="field-control"
        />
      </div>
    </div>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="readonly" class="form-hint">该表单来自历史记录，仅供查看，不可再提交。</p>
    <p v-else-if="submitted" class="form-hint">已提交，正在等待客服回复…</p>

    <div v-if="!readonly" class="form-actions">
      <el-button
        type="primary"
        size="small"
        :disabled="locked"
        @click="submit"
      >
        {{ form.submitLabel || '提交' }}
      </el-button>
      <span v-if="form.expiresInSeconds > 0" class="form-hint">
        请在约 {{ Math.round(form.expiresInSeconds / 60) }} 分钟内提交
      </span>
    </div>
  </section>
</template>

<style scoped>
.form-card {
  margin-top: 12px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #f2cede;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(214, 51, 108, 0.06);
}

.form-card.locked {
  background: #fcfcfd;
}

.form-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.form-title {
  flex: 1;
  min-width: 0;
}

.form-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.form-desc {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-3);
}

.form-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-label {
  font-size: 12px;
  color: var(--c-text-2);
}

.required {
  margin-left: 2px;
  color: #d9534f;
}

.field-control {
  width: 100%;
}

.form-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: #d9534f;
}

.form-hint {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--c-text-3);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.form-actions .form-hint {
  margin: 0;
}
</style>

import { get, post } from './http'
import type { PageResponse, ProductAnswerView, ProductQuestionView } from './types'

export type QuestionFilter = 'all' | 'unanswered' | 'answered'

/** 商品提问列表，公开可读；每条带最多两条回答预览 */
export function fetchProductQuestions(
  productId: number,
  page = 1,
  size = 5,
  filter: QuestionFilter = 'all',
): Promise<PageResponse<ProductQuestionView>> {
  return get<PageResponse<ProductQuestionView>>(
    `/products/${productId}/questions`,
    { page, size, filter },
  )
}

/** 任何登录用户都可以提问，提问不参与商品评分 */
export function createProductQuestion(productId: number, content: string): Promise<void> {
  return post<void>(`/products/${productId}/questions`, { content })
}

export function fetchQuestionAnswers(
  productId: number,
  questionId: number,
  page = 1,
  size = 10,
): Promise<PageResponse<ProductAnswerView>> {
  return get<PageResponse<ProductAnswerView>>(
    `/products/${productId}/questions/${questionId}/answers`,
    { page, size },
  )
}

/** 只有买过该商品的用户可以回答 */
export function createQuestionAnswer(
  productId: number,
  questionId: number,
  content: string,
): Promise<void> {
  return post<void>(`/products/${productId}/questions/${questionId}/answers`, { content })
}

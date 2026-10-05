import { defineStore } from 'pinia'
import { createBookingNavigation } from './bookingNavigationState'

/** 通知与服务页之间只传递单次内存意图，禁止使用URL或本地存储。 */
export const useBookingNavigationStore = defineStore('booking-navigation', createBookingNavigation)

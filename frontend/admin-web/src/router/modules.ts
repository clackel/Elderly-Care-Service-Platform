export const featureModules = [
  { path: 'elders', title: '老人档案', description: '老人基础资料与社区归属。' },
  { path: 'health', title: '健康协助录入', description: '获授权老人及本人代录记录的维护。' },
  {
    path: 'consent',
    title: '账号与预约授权',
    description: '移动账号核验、本人绑定与家属预约授权。',
  },
  { path: 'services', title: '服务目录与人员', description: '五类服务项目、提供方与人员能力。' },
  { path: 'bookings', title: '养老服务预约', description: '社区代预约、确认安排与处理进度。' },
  { path: 'fulfillment', title: '服务履约', description: '执行进度、异常处理与服务结果。' },
  { path: 'emergencies', title: '求助工作台', description: '值守接警与人工联络记录。' },
] as const

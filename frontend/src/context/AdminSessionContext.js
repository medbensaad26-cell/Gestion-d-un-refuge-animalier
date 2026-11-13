import { createContext, useContext } from 'react'
export const AdminSessionContext = createContext(null)
export function useAdminSession() {
  const context = useContext(AdminSessionContext)
  if (!context) throw new Error('AdminSessionProvider is required')
  return context
}

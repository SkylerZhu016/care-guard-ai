export type Role = 'patient' | 'clinician' | 'followup' | 'admin'

export interface RoleMeta {
  id: Role
  label: string
  shortLabel: string
  description: string
}

export type Tone = 'green' | 'amber' | 'red' | 'blue' | 'gray'

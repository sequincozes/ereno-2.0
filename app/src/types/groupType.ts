export interface GroupType {
  name: string;
  ieds: string[]; 
} 

export interface groupFormProps {
  groupIndex: string;
  onDeleteForm?: () => void;
}
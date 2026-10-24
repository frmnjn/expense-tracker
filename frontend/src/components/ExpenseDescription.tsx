import { List, Text, type MantineColor, type MantineSize } from '@mantine/core'
import { splitDescriptionItems } from '../utils/description'

export function ExpenseDescription({
  description,
  size = 'sm',
  color,
}: {
  description: string
  size?: MantineSize
  color?: MantineColor
}) {
  const items = splitDescriptionItems(description)

  if (items) {
    return (
      <List size={size} c={color} spacing={2}>
        {items.map((item, index) => (
          <List.Item key={`${index}-${item}`}>{item}</List.Item>
        ))}
      </List>
    )
  }

  return (
    <Text size={size} c={color} style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      {description}
    </Text>
  )
}

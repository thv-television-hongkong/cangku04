<template>
  <div ref="chart" :style="{ height: height + 'px', width: '100%' }" />
</template>

<script>
import { markRaw } from 'vue'
export default {
  name: 'StatChart',
  props: {
    /** 图表类型：pie / bar / line */
    type: { type: String, default: 'pie' },
    title: { type: String, default: '' },
    data: { type: Array, default: () => [] },
    height: { type: Number, default: 300 },
    /** 数据字段名 */
    nameField: { type: String, default: 'name' },
    valueField: { type: String, default: 'value' }
  },
  data() {
    return { chart: null }
  },
  watch: {
    data: {
      deep: true,
      handler() { this.render() }
    }
  },
  mounted() {
    this.render()
    window.addEventListener('resize', this.resize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.resize)
    if (this.chart) this.chart.dispose()
  },
  methods: {
    resize() {
      if (this.chart) this.chart.resize()
    },
    render() {
      if (!this.$refs.chart) return
      if (!this.chart) {
        this.chart = markRaw(this.$echarts.init(this.$refs.chart))
      }
      const names = (this.data || []).map(i => i[this.nameField])
      const values = (this.data || []).map(i => Number(i[this.valueField] || 0))
      let option
      if (this.type === 'pie') {
        option = {
          title: { text: this.title, left: 'center', textStyle: { fontSize: 14 } },
          tooltip: { trigger: 'item' },
          legend: { bottom: 0 },
          series: [{
            type: 'pie',
            radius: ['40%', '65%'],
            center: ['50%', '50%'],
            data: names.map((n, i) => ({ name: n, value: values[i] })),
            label: { formatter: '{b}: {c}' }
          }]
        }
      } else if (this.type === 'line') {
        option = {
          title: { text: this.title, left: 'center', textStyle: { fontSize: 14 } },
          tooltip: { trigger: 'axis' },
          grid: { left: 50, right: 30, top: 50, bottom: 50 },
          xAxis: { type: 'category', data: names },
          yAxis: { type: 'value' },
          series: [{ type: 'line', smooth: true, areaStyle: {}, data: values }]
        }
      } else {
        option = {
          title: { text: this.title, left: 'center', textStyle: { fontSize: 14 } },
          tooltip: { trigger: 'axis' },
          grid: { left: 50, right: 30, top: 50, bottom: 60 },
          xAxis: { type: 'category', data: names, axisLabel: { rotate: 20 } },
          yAxis: { type: 'value' },
          series: [{ type: 'bar', barMaxWidth: 40, data: values, itemStyle: { color: '#409eff' } }]
        }
      }
      this.chart.setOption(option, true)
    }
  }
}
</script>
